package com.example.proxy.core.notifier;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.proxy.core.server.ForwardRequest;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

/**
 * Aggregates responses from multiple backend destinations before sending 
 * a single combined response back to the client.
 */
public class NotifierResponseAggregator {
    
    private static final Logger logger = LoggerFactory.getLogger(NotifierResponseAggregator.class);
    
    private final List<ResponseData> responses = new ArrayList<>();
    private final AtomicInteger expectedResponses;
    private final AtomicInteger receivedResponses = new AtomicInteger(0);
    private final ChannelHandlerContext clientCtx;
    private final String requestId;
    private volatile boolean responsesSent = false;
    private ScheduledFuture<?> timeoutTask;
    
    // Static map to track aggregators by request ID
    private static final Map<String, NotifierResponseAggregator> activeAggregators = new ConcurrentHashMap<>();
    
    public NotifierResponseAggregator(ChannelHandlerContext ctx, ForwardRequest request, int expectedCount) {
        this.clientCtx = ctx;
        this.expectedResponses = new AtomicInteger(expectedCount);
        this.requestId = generateRequestId(ctx, request);
        
        activeAggregators.put(requestId, this);
        
        this.timeoutTask = ctx.executor().schedule(() -> {
            logger.warn("Timeout reached for request {}, sending partial response with {}/{} responses", 
                       requestId, receivedResponses.get(), expectedResponses.get());
            sendAggregatedResponse();
        }, 10, TimeUnit.SECONDS);
        
        logger.info("Created response aggregator for request {} expecting {} responses", requestId, expectedCount);
    }
    
    private String generateRequestId(ChannelHandlerContext ctx, ForwardRequest request) {
        return ctx.channel().id().asShortText() + "-" + System.currentTimeMillis();
    }
    
    /**
     * Add a response from one of the backend destinations.
     * When all expected responses are received, sends the aggregated response to client.
     */
    public synchronized void addResponse(Object response, String source) {
        if (responsesSent) {
            logger.debug("Response already sent for request {}, ignoring response from {}", requestId, source);
            return;
        }
        
        ResponseData responseData = new ResponseData(source, response, System.currentTimeMillis());
        responses.add(responseData);
        
        int received = receivedResponses.incrementAndGet();
        logger.info("Response aggregator {}: received {} of {} from {}", requestId, received, expectedResponses.get(), source);
        
        if (received >= expectedResponses.get()) {
            sendAggregatedResponse();
        }
    }
    
    /**
     * Get an existing aggregator for a request, or null if not found
     */
    public static NotifierResponseAggregator getAggregator(String requestId) {
        return activeAggregators.get(requestId);
    }
    
    /**
     * Creates and sends a JSON response containing all backend responses
     */
    private void sendAggregatedResponse() {
        if (responsesSent) {
            logger.debug("Response already sent for request {}", requestId);
            return;
        }
        responsesSent = true;
        
        // Cancel timeout task
        if (timeoutTask != null && !timeoutTask.isDone()) {
            timeoutTask.cancel(false);
        }
        
        // Clean up
        activeAggregators.remove(requestId);
        
        try {
            StringBuilder json = new StringBuilder();
            json.append("{\n");
            json.append("  \"request_id\": \"").append(requestId).append("\",\n");
            json.append("  \"total_responses\": ").append(responses.size()).append(",\n");
            json.append("  \"responses\": [\n");
            
            for (int i = 0; i < responses.size(); i++) {
                if (i > 0) json.append(",\n");
                
                ResponseData responseData = responses.get(i);
                json.append("    {\n");
                json.append("      \"source\": \"").append(responseData.source).append("\",\n");
                json.append("      \"timestamp\": ").append(responseData.timestamp).append(",\n");
                
                if (responseData.response instanceof FullHttpResponse) {
                    FullHttpResponse httpResp = (FullHttpResponse) responseData.response;
                    json.append("      \"status_code\": ").append(httpResp.status().code()).append(",\n");
                    json.append("      \"status_text\": \"").append(httpResp.status().reasonPhrase()).append("\",\n");
                    json.append("      \"headers\": {\n");
                    
                    boolean firstHeader = true;
                    for (Map.Entry<String, String> header : httpResp.headers()) {
                        if (!firstHeader) json.append(",\n");
                        json.append("        \"").append(escapeJson(header.getKey())).append("\": \"")
                           .append(escapeJson(header.getValue())).append("\"");
                        firstHeader = false;
                    }
                    json.append("\n      },\n");
                    
                    // Use pre-captured body content
                    String body = escapeJson(responseData.bodyContent);
                    json.append("      \"body\": \"").append(body).append("\"\n");
                } else {
                    json.append("      \"error\": \"Invalid response type: ").append(responseData.response.getClass().getSimpleName()).append("\"\n");
                }
                json.append("    }");
            }
            
            json.append("\n  ]\n}");
            
            DefaultFullHttpResponse aggregatedResponse = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK,
                clientCtx.alloc().buffer().writeBytes(json.toString().getBytes(StandardCharsets.UTF_8))
            );
            
            aggregatedResponse.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=utf-8");
            aggregatedResponse.headers().set(HttpHeaderNames.CONTENT_LENGTH, json.length());
            aggregatedResponse.headers().set("X-Proxy-Server", "ahc-proxy-cluster");
            aggregatedResponse.headers().set("X-Response-Count", responses.size());
            
            clientCtx.writeAndFlush(aggregatedResponse).addListener(future -> {
                if (future.isSuccess()) {
                    logger.info("Aggregated response sent to client with {} responses", responses.size());
                } else {
                    logger.error("Failed to send aggregated response: {}", future.cause().getMessage());
                }
            });
            
        } catch (Exception e) {
            logger.error("Error creating aggregated response: {}", e.getMessage());
            sendErrorResponse("Failed to aggregate responses: " + e.getMessage());
        }
    }
    
    private void sendErrorResponse(String errorMessage) {
        try {
            String errorJson = "{\"error\": \"Cluster Response Error\", \"message\": \"" + escapeJson(errorMessage) + "\"}";
            
            DefaultFullHttpResponse errorResponse = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.INTERNAL_SERVER_ERROR,
                clientCtx.alloc().buffer().writeBytes(errorJson.getBytes(StandardCharsets.UTF_8))
            );
            
            errorResponse.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
            errorResponse.headers().set(HttpHeaderNames.CONTENT_LENGTH, errorJson.length());
            
            clientCtx.writeAndFlush(errorResponse);
        } catch (Exception e) {
            logger.error("Failed to send error response: {}", e.getMessage());
        }
    }
    
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                 .replace("\"", "\\\"")
                 .replace("\n", "\\n")
                 .replace("\r", "\\r")
                 .replace("\t", "\\t");
    }
    
    /**
     * Data container for individual backend responses with captured content
     */
    private static class ResponseData {
        final String source;
        final Object response;
        final long timestamp;
        final String bodyContent; 
        
        ResponseData(String source, Object response, long timestamp) {
            this.source = source;
            this.response = response;
            this.timestamp = timestamp;
            
            String capturedBody = "";
            if (response instanceof FullHttpResponse) {
                FullHttpResponse httpResp = (FullHttpResponse) response;
                try {
                    if (httpResp.content() != null && httpResp.content().isReadable()) {
                        capturedBody = httpResp.content().toString(StandardCharsets.UTF_8);
                    }
                } catch (Exception e) {
                    capturedBody = "Error reading response body: " + e.getMessage();
                }
            }
            this.bodyContent = capturedBody;
        }
    }
}
