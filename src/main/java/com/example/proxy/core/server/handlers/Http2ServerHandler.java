package com.example.proxy.core.server.handlers;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.proxy.core.backend.BackendTarget;
import com.example.proxy.core.server.ForwardHttp2;
import com.example.proxy.core.server.ForwardRequest;
import com.example.proxy.core.stages.AuthStage;
import com.example.proxy.core.stages.CompressionStage;
import com.example.proxy.utils.HttpUtil;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.handler.codec.http2.Http2StreamFrame;

/* 
 *  Overview: Handles HTTP/2 frames, stream multiplexing, and flow control
 */

public class Http2ServerHandler extends SimpleChannelInboundHandler<Http2StreamFrame> implements ServerHandler {

    private static final Logger logger = LoggerFactory.getLogger(Http2ServerHandler.class);
    private final Map<Integer, ForwardHttp2> pendingRequests = new HashMap<>();

    @Override
    public ForwardRequest parseIncomingMessage(ChannelHandlerContext ctx, Object message) {
        if (!(message instanceof Http2StreamFrame)) {
            return null;
        }

        Http2StreamFrame streamFrame = (Http2StreamFrame) message;
        if (streamFrame instanceof Http2HeadersFrame) {
            ForwardHttp2 request = handleHeadersFrame(ctx, (Http2HeadersFrame) streamFrame);
            int streamId = streamFrame.stream().id();
            
            if (!request.isBody()) {
                // Complete request (GET/HEAD) - return immediately
                return request;
            } else {
                // Request with body - store for later completion
                pendingRequests.put(streamId, request);
                return null; // Wait for DATA frames
            }
        } else if (streamFrame instanceof Http2DataFrame) {
            return handleDataFrame(ctx, (Http2DataFrame) streamFrame);
        } else {
            logger.debug("Received other HTTP/2 frame type: {} for stream {}", 
                streamFrame.getClass().getSimpleName(), streamFrame.stream().id());
            return null;
        }
    }

    @Override
    public BackendTarget routeToBackend(ForwardRequest request) {
        if (!(request instanceof ForwardHttp2)) {
            logger.error("Invalid request type for HTTP/2 handler");
            return null;
        }

        ForwardHttp2 http2Request = (ForwardHttp2) request;
        String uri = http2Request.getURI();
        String authority = http2Request.getAuthority();
        String path = http2Request.getPath();

        try {
            String host;
            int port;
            String targetPath = path != null ? path : "/";

            // Extract host and port from authority or URI
            if (authority != null) {
                // Parse authority (host:port format)
                if (authority.contains(":")) {
                    String[] parts = authority.split(":");
                    host = parts[0];
                    port = Integer.parseInt(parts[1]);
                } else {
                    host = authority;
                    port = 80; // Default HTTP port
                }
            } else {
                // Fallback to extracting from URI
                host = HttpUtil.getHostFromURI(uri);
                port = HttpUtil.getPortFromURI(uri);
                targetPath = HttpUtil.getPathFromURI(uri);
            }

            if (host == null) {
                logger.error("Unable to determine target host from HTTP/2 request");
                return null;
            }

            // Create metadata for backend configuration
            Map<String, String> metadata = new HashMap<>();
            metadata.put("protocol", "http2");
            
            // Add authentication metadata if present
            if (request.hasAuth()) {
                AuthStage authStage = request.getAuthStage();
                if (authStage != null) {
                    metadata.put("auth", authStage.getAlg());
                }
            }
            
            // Add compression metadata if present
            if (request.hasCompression()) {
                CompressionStage compStage = request.getCompressionStage();
                if (compStage != null) {
                    metadata.put("comp", compStage.getAlg());
                }
            }

            logger.info("Routing HTTP/2 request to {}:{}{}", host, port, targetPath);
            return new BackendTarget(host, port, targetPath, metadata);
            
        } catch (Exception e) {
            logger.error("Error routing HTTP/2 request: {}", e.getMessage());
            return null;
        }
    }   
    
    @Override
    public boolean forwardToBackend(ChannelHandlerContext ctx, ForwardRequest request, BackendTarget target) {
        if (!(request instanceof ForwardHttp2)) {
            logger.error("Invalid request type for HTTP/2");
            return false;
        }

        ForwardHttp2 httpRequest = (ForwardHttp2) request;

        throw new UnsupportedOperationException("Unimplemented method 'forwardToBackend'");
    }

    @Override
    public Object processBackendResponse(ChannelHandlerContext ctx, Object backendResponse) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'processBackendResponse'");
    }

    @Override
    public void sendResponseToClient(ChannelHandlerContext ctx, Object response, ForwardRequest originalRequest) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'sendResponseToClient'");
    }

    @Override
    public void handleError(ChannelHandlerContext ctx, Throwable cause, ForwardRequest request) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'handleError'");
    }

    @Override
    public void cleanup(ChannelHandlerContext ctx) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'cleanup'");
    }

    @Override
    public String getProtocolType() {
        return "HTTP2";
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Http2StreamFrame msg) throws Exception {
        try {
            ForwardRequest request = parseIncomingMessage(ctx, msg);
            if (request != null) {
                // Complete request received - process it
                BackendTarget target = routeToBackend(request);
                if (target != null) {
                    boolean success = forwardToBackend(ctx, request, target);
                    if (!success) {
                        handleError(ctx, new Exception("Failed to forward request"), request);
                    }
                } else {
                    handleError(ctx, new Exception("Failed to create backend target"), request);
                }
            }
            // If request is null, we're waiting for more frames (normal for DATA frames)
        } catch (Exception e) {
            logger.error("Error processing HTTP/2 frame: {}", e.getMessage(), e);
        }
    }
    
    private ForwardHttp2 handleHeadersFrame(ChannelHandlerContext ctx, Http2HeadersFrame headersFrame) {
        Http2Headers headers = headersFrame.headers();
        int streamId = headersFrame.stream().id();
        
        logger.debug("Received HEADERS frame for stream {}", streamId);
        
        // Extract HTTP/2 pseudo-headers  
        String method = headers.method() != null ? headers.method().toString() : "GET";
        String path = headers.path() != null ? headers.path().toString() : "/";
        String authority = headers.authority() != null ? headers.authority().toString() : null;
        
        // Convert headers to map
        Map<String, String> headerMap = new HashMap<>();
        for (Map.Entry<CharSequence, CharSequence> entry : headers) {
            headerMap.put(entry.getKey().toString(), entry.getValue().toString());
        }
        
        // For requests without body (GET, etc.) or with END_STREAM flag
        if ("GET".equals(method) || "HEAD".equals(method) || headersFrame.isEndStream()) {
            // Complete request - process immediately
            ByteBuf emptyData = ctx.alloc().buffer(0);
            ForwardHttp2 request = new ForwardHttp2(emptyData, method, path, authority, headerMap, streamId, 0, false);
            
            // Apply compression settings
            String acceptEncoding = headerMap.get("accept-encoding");
            if (acceptEncoding != null) {
                if (acceptEncoding.contains("gzip")) {
                    request.withCompression("gzip");
                } else if (acceptEncoding.contains("deflate")) {
                    request.withCompression("deflate");
                } else if (acceptEncoding.contains("br")) {
                    request.withCompression("brotli");
                }
            }
            
            // Apply authentication settings
            String authorization = headerMap.get("authorization");
            if (authorization != null) {
                if (authorization.startsWith("Bearer ")) {
                    request.withAuth("bearer");
                } else if (authorization.startsWith("Basic ")) {
                    request.withAuth("basic");
                } else if (authorization.startsWith("OAuth ")) {
                    request.withAuth("oauth");
                }
            }

            return request;
        } else {
            logger.debug("Waiting for DATA frames for stream {}", streamId);
            ByteBuf emptyData = ctx.alloc().buffer(0);
            ForwardHttp2 request = new ForwardHttp2(emptyData, method, path, authority, headerMap, streamId, 0, true);   
            
            // Apply compression settings
            String acceptEncoding = headerMap.get("accept-encoding");
            if (acceptEncoding != null) {
                if (acceptEncoding.contains("gzip")) {
                    request.withCompression("gzip");
                } else if (acceptEncoding.contains("deflate")) {
                    request.withCompression("deflate");
                } else if (acceptEncoding.contains("br")) {
                    request.withCompression("brotli");
                }
            }
            
            // Apply authentication settings
            String authorization = headerMap.get("authorization");
            if (authorization != null) {
                if (authorization.startsWith("Bearer ")) {
                    request.withAuth("bearer");
                } else if (authorization.startsWith("Basic ")) {
                    request.withAuth("basic");
                } else if (authorization.startsWith("OAuth ")) {
                    request.withAuth("oauth");
                }
            }

            return request;
        }
    }
    
    private ForwardHttp2 handleDataFrame(ChannelHandlerContext ctx, Http2DataFrame dataFrame) {
        int streamId = dataFrame.stream().id();
        ByteBuf content = dataFrame.content();
        
        logger.debug("Received DATA frame for stream {}, {} bytes, endStream: {}", 
            streamId, content.readableBytes(), dataFrame.isEndStream());
        
        // Get the pending request for this stream
        ForwardHttp2 pendingRequest = pendingRequests.get(streamId);
        if (pendingRequest == null) {
            logger.warn("Received DATA frame for unknown stream {}", streamId);
            return null;
        }
        
        // Append data to the existing request
        if (content.isReadable()) {
            ByteBuf currentData = pendingRequest.getData();
            ByteBuf newData = ctx.alloc().buffer(currentData.readableBytes() + content.readableBytes());
            newData.writeBytes(currentData);
            newData.writeBytes(content);
            
            // Create new request with combined data
            pendingRequest = new ForwardHttp2(newData, 
                pendingRequest.getMethod(), 
                pendingRequest.getPath(), 
                pendingRequest.getAuthority(), 
                pendingRequest.getHeaders(), 
                pendingRequest.getHeaderId(), 
                pendingRequest.getDataId(), 
                true);
            
            // Update stored request
            pendingRequests.put(streamId, pendingRequest);
            
            // Release old buffer
            currentData.release();
        }
        
        if (dataFrame.isEndStream()) {
            logger.debug("End of stream for {}", streamId);
            // Remove from pending and return completed request
            ForwardHttp2 completedRequest = pendingRequests.remove(streamId);
            return completedRequest;
        }
        
        // More data frames expected
        return null;
    }
    
}