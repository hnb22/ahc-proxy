package com.example.proxy.core.server.handlers;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.proxy.core.backend.BackendResponseCallback;
import com.example.proxy.core.backend.BackendTarget;
import com.example.proxy.core.backend.HttpBackendClient;
import com.example.proxy.core.backend.custom.BackendCallbackHttp1;
import com.example.proxy.core.server.ForwardHttp1;
import com.example.proxy.core.server.ForwardRequest;
import com.example.proxy.core.stages.ContentFilterStage;
import com.example.proxy.utils.HttpUtil;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

public class Http1ServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> implements ServerHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(Http1ServerHandler.class);
        
    public Http1ServerHandler() {
    }
    
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
    }

    @Override
    public ForwardRequest parseIncomingMessage(ChannelHandlerContext ctx, Object message) {
        if (!(message instanceof FullHttpRequest)) {
            return null;
        }

        FullHttpRequest rqst = (FullHttpRequest) message;
        try {
            String method = rqst.method().name();
            String uri = rqst.uri();
            ByteBuf body = rqst.content().copy();

            Map<String, String> headers = new HashMap<>();
            for (Map.Entry<String, String> entry : rqst.headers()) {
                headers.put(entry.getKey().toLowerCase(), entry.getValue());
            }

            String clientAddress = ctx.channel().remoteAddress().toString();
            
            ForwardHttp1 forwardRequest = new ForwardHttp1(body, method, uri, headers, clientAddress);
            
            String acceptEncoding = headers.get("accept-encoding");
            if (acceptEncoding != null) {
                if (acceptEncoding.contains("gzip")) {
                    forwardRequest.withCompression("gzip");
                } else if (acceptEncoding.contains("deflate")) {
                    forwardRequest.withCompression("deflate");
                } else if (acceptEncoding.contains("br")) {
                    forwardRequest.withCompression("brotli");
                }
            }
            
            String authorization = headers.get("authorization");
            if (authorization != null) {
                if (authorization.startsWith("Bearer ")) {
                    forwardRequest.withAuth("bearer");
                } else if (authorization.startsWith("Basic ")) {
                    forwardRequest.withAuth("basic");
                } else if (authorization.startsWith("OAuth ")) {
                    forwardRequest.withAuth("oauth");
                }
            }
            
            return forwardRequest;

        } catch (Exception e) {
            logger.error("Error parsing HTTP request: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public BackendTarget routeToBackend(ForwardRequest request) {
        try {
            if (!(request instanceof ForwardHttp1)) {
                logger.error("Invalid request type for HTTP/1.1 handler");
                return null;
            }

            ContentFilterStage filter = new ContentFilterStage();
            ContentFilterStage.FilterDecision decision = filter.evaluateRequest(request);
            
            if (decision.isBlocked()) {
                logger.warn("Request blocked by content filter: {}", decision.getReason());
                // Return null to indicate the request should be blocked
                return null;
            }
            
            ForwardHttp1 rqstHttp = (ForwardHttp1) request;
            String uri = rqstHttp.getURI();
            Map<String, String> headers = rqstHttp.getHeaders();

            String host;
            int port;
            String path = "";
            //HTTPS tunneling
            if ("CONNECT".equals(rqstHttp.getMethod())) {
                //TODO: add helper method in HttpUtil
                String[] both = uri.split(":");
                host = both[0];
                port = Integer.parseInt(both[1]);
            //Regular HTTP
            } else {
                host = HttpUtil.getHostFromURI(uri);
                path = HttpUtil.getPathFromURI(uri);
                port = HttpUtil.getPortFromURI(uri);
            }

            Map<String, String> metadata = new HashMap<>();
            metadata.put("protocol", "HTTP/1.1");
            
            if (rqstHttp.hasAuth()) {
                metadata.put("auth", rqstHttp.getAuthStage().getAlg());
            }
            if (rqstHttp.hasCompression()) {
                metadata.put("comp", rqstHttp.getCompressionStage().getAlg());
            }

            return new BackendTarget(host, port, path, metadata);

        } catch (Exception e) {
            logger.error("Error routing request: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public boolean forwardToBackend(ChannelHandlerContext ctx, ForwardRequest request, BackendTarget target) {
        try {
            if (!(request instanceof ForwardHttp1)) {
                logger.error("Invalid request type for HTTP1");
                return false;
            }

            ForwardHttp1 httpRequest = (ForwardHttp1) request;
            String auth = target.getMetadata().get("auth");
            String comp = target.getMetadata().get("comp");

            HttpBackendClient backendClient = new HttpBackendClient(ctx.channel().eventLoop(),
                                                                    auth != null ? auth : "none",
                                                                    comp != null ? comp : "none");
            
            BackendCallbackHttp1.ResponseProcessor responseProcessor = new BackendCallbackHttp1.ResponseProcessor() {
                @Override
                public Object processBackendResponse(ChannelHandlerContext ctx, Object backendResponse) {
                    return Http1ServerHandler.this.processBackendResponse(ctx, backendResponse);
                }
                
                @Override
                public void sendResponseToClient(ChannelHandlerContext ctx, Object response, ForwardRequest originalRequest) {
                    Http1ServerHandler.this.sendResponseToClient(ctx, response, originalRequest);
                }
                
                @Override
                public void handleError(ChannelHandlerContext ctx, Throwable cause, ForwardRequest request) {
                    Http1ServerHandler.this.handleError(ctx, cause, request);
                }
            };

            BackendResponseCallback callback = new BackendCallbackHttp1(ctx, httpRequest, responseProcessor, target);
            
            //HTTPS Tunneling
            if ("CONNECT".equals(httpRequest.getMethod())) {
                FullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1,
                    new HttpResponseStatus(200, "Connection Established")
                );
                ctx.writeAndFlush(response);
                
                backendClient.forwardRequestHTTPS(ctx, httpRequest, target, callback)
                    .whenComplete((success, throwable) -> {
                        if (throwable != null) {
                            handleError(ctx, throwable, httpRequest);
                        } else if (!success) {
                            handleError(ctx, new Exception("Failed to establish connection"), httpRequest);
                        }
                    });

            } else {
                backendClient.forwardRequestHTTP(httpRequest, target, callback)
                    .whenComplete((success, throwable) -> {
                        if (throwable != null) {
                            handleError(ctx, throwable, httpRequest);
                        } else if (!success) {
                            handleError(ctx, new Exception("Failed to establish connection"), httpRequest);
                        }
                    });
            }              
            return true;
            
        } catch (Exception e) {
            logger.error("Error forwarding to backend: {}", e.getMessage());
            handleError(ctx, e, request);
            return false;
        }
    }
    
    @Override
    public void sendResponseToClient(ChannelHandlerContext ctx, Object response, ForwardRequest originalRequest) {
        try {
            if (ctx.channel().isActive() && response instanceof FullHttpResponse) {
                ctx.writeAndFlush(response).addListener(future -> {
                    if (future.isSuccess()) {
                        logger.info("HTTP/1.1 response sent to client");
                    } else {
                        logger.error("Failed to send response: {}", future.cause().getMessage());
                    }
                });
            }
        } catch (Exception e) {
            logger.error("Error sending HTTP/1.1 response: {}", e.getMessage());
        }
    }

    @Override
    public void handleError(ChannelHandlerContext ctx, Throwable cause, ForwardRequest request) {
        if (!ctx.channel().isActive()) return;
        
        try {
            String errorJson = "{\"error\":\"HTTP/1.1 Proxy Error\",\"message\":\"" + cause.getMessage() + "\"}";
            
            DefaultFullHttpResponse errorResponse = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, 
                HttpResponseStatus.INTERNAL_SERVER_ERROR,
                ctx.alloc().buffer().writeBytes(errorJson.getBytes())
            );
            
            errorResponse.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
            errorResponse.headers().set(HttpHeaderNames.CONTENT_LENGTH, errorJson.length());
            
            ctx.writeAndFlush(errorResponse).addListener(ChannelFutureListener.CLOSE);
            
        } catch (Exception e) {
            logger.error("Error sending HTTP/1.1 error response: {}", e.getMessage());
            ctx.close();
        }    
    }

    @Override
    public void cleanup(ChannelHandlerContext ctx) {   
    }

    @Override
    public String getProtocolType() {
        return "HTTP/1.1";
    }

    @Override
    public Object processBackendResponse(ChannelHandlerContext ctx, Object backendResponse) {
        if (!(backendResponse instanceof FullHttpResponse)) {
            return backendResponse;
        }
        
        FullHttpResponse response = (FullHttpResponse) backendResponse;
        
        response.headers().set("X-Proxy-Server", "ahc-proxy-http1");
        
        response.headers().remove(HttpHeaderNames.CONNECTION);
        response.headers().remove("Proxy-Connection");
        
        logger.info("Processed HTTP/1.1 response");
        
        return response;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest msg) throws Exception {
        try {
            ForwardRequest request = parseIncomingMessage(ctx, msg);
            if (request != null) {
                
                ContentFilterStage filter = new ContentFilterStage();
                ContentFilterStage.FilterDecision decision = filter.evaluateRequest(request);
                
                if (decision.isBlocked()) {
                    sendBlockedResponse(ctx, decision.getReason());
                    return;
                }
                
                BackendTarget target = routeToBackend(request);
                if (target != null) {
                    boolean success = forwardToBackend(ctx, request, target);
                    if (!success) {
                        handleError(ctx, new Exception("Failed to forward request"), request);
                    }
                } else {
                    handleError(ctx, new Exception("No backend target found"), request);
                }
            } else {
                handleError(ctx, new Exception("Failed to parse request"), null);
            }
        } catch (Exception e) {
            handleError(ctx, e, null);
        }
    }
        
    /**
     * Send a blocked response to the client when content filter blocks the request
     */
    private void sendBlockedResponse(ChannelHandlerContext ctx, String reason) {
        if (!ctx.channel().isActive()) return;
        
        try {
            String blockedHtml = createBlockedPageHtml(reason);
            
            DefaultFullHttpResponse blockedResponse = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, 
                HttpResponseStatus.FORBIDDEN,
                ctx.alloc().buffer().writeBytes(blockedHtml.getBytes("UTF-8"))
            );
            
            blockedResponse.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html; charset=UTF-8");
            blockedResponse.headers().set(HttpHeaderNames.CONTENT_LENGTH, blockedHtml.getBytes("UTF-8").length);
            blockedResponse.headers().set("X-Proxy-Server", "ahc-proxy");
            blockedResponse.headers().set("X-Content-Filter", "blocked");
            
            ctx.writeAndFlush(blockedResponse).addListener(ChannelFutureListener.CLOSE);
            
            logger.info("Sent content filter blocked response: {}", reason);
            
        } catch (Exception e) {
            logger.error("Error sending blocked response: {}", e.getMessage());
            ctx.close();
        }
    }
    

    private String createBlockedPageHtml(String reason) {
        return "<!DOCTYPE html>\n" +
               "<html><head><title>Access Blocked</title>\n" +
               "<style>body{font-family:Arial,sans-serif;margin:40px;text-align:center;}\n" +
               ".blocked{color:#d32f2f;font-size:24px;margin:20px 0;}\n" +
               ".reason{color:#666;font-size:16px;margin:20px 0;}\n" +
               ".footer{color:#999;font-size:12px;margin-top:40px;}</style></head>\n" +
               "<body>\n" +
               "<h1 class=\"blocked\">🚫 Access Blocked</h1>\n" +
               "<p class=\"reason\">" + escapeHtml(reason) + "</p>\n" +
               "<p>This request was blocked by the proxy server's content filter.</p>\n" +
               "<p>If you believe this is an error, please contact your system administrator.</p>\n" +
               "<div class=\"footer\">AHC Proxy Server</div>\n" +
               "</body></html>";
    }
    
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                  .replace("<", "&lt;")
                  .replace(">", "&gt;")
                  .replace("\"", "&quot;")
                  .replace("'", "&#x27;");
    }
    
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.info("Client channel became inactive: {} - pipeline: {}", ctx.channel().id(), 
                   ctx.channel().pipeline().names());
        super.channelInactive(ctx);
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("Exception in Http1ServerHandler: {}", cause.getMessage());
        super.exceptionCaught(ctx, cause);
    }
}