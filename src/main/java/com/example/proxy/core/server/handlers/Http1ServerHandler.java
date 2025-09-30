package com.example.proxy.core.server.handlers;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.proxy.core.backend.BackendResponseCallback;
import com.example.proxy.core.backend.BackendTarget;
import com.example.proxy.core.backend.HttpBackendClient;
import com.example.proxy.core.backend.custom.BackendCallbackHttp1;
import com.example.proxy.core.server.ForwardHttp1;
import com.example.proxy.core.server.ForwardRequest;
import com.example.proxy.utils.HttpUtil;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

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

/* 
 *  Overview: Handles HTTP/1.x requests including reading body, passing to routing layer
 */

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
                logger.info("Starting direct HTTPS tunnel to {}:{}", target.getHost(), target.getPort());
                
                // Send 200 response immediately
                FullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1,
                    new HttpResponseStatus(200, "Connection Established")
                );
                
                ctx.writeAndFlush(response).addListener(future -> {
                    if (future.isSuccess()) {
                        logger.info("200 response sent, establishing direct tunnel");
                        establishDirectTunnel(ctx, target);
                    } else {
                        logger.error("Failed to send 200 response: {}", future.cause().getMessage());
                        ctx.close();
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

            return false;

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
    
    private void setupTunnelRelay(Channel backendChannel, Queue<Object> buffer, Channel clientChannel) {
        logger.info("Setting up tunnel relay - buffer: {}, client active: {}, backend active: {}", 
                   buffer != null ? buffer.size() : "null", clientChannel.isActive(), backendChannel.isActive());
        
        // Forward any buffered data first
        if (buffer != null) {
            while (!buffer.isEmpty()) {
                Object msg = buffer.poll();
                if (msg != null) {
                    logger.info("Forwarding buffered message: {}", msg.getClass().getSimpleName());
                    backendChannel.writeAndFlush(msg);
                }
            }
        }
        
        // Remove buffer handler if it exists
        if (clientChannel.pipeline().get("buffer-handler") != null) {
            logger.info("Removing buffer handler from client pipeline");
            clientChannel.pipeline().remove("buffer-handler");
        }
        
        // Set up bidirectional relay
        logger.info("Adding relay handlers");
        clientChannel.pipeline().addLast(new RelayHandler(backendChannel, "client->backend"));
        backendChannel.pipeline().addLast(new RelayHandler(clientChannel, "backend->client"));
        logger.info("Tunnel relay setup complete");
    }
    
    private void establishDirectTunnel(ChannelHandlerContext ctx, BackendTarget target) {
        logger.info("Establishing direct tunnel to {}:{}", target.getHost(), target.getPort());
        
        // Create backend connection
        io.netty.bootstrap.Bootstrap bootstrap = new io.netty.bootstrap.Bootstrap();
        bootstrap.group(ctx.channel().eventLoop())
                .channel(io.netty.channel.socket.nio.NioSocketChannel.class)
                .handler(new io.netty.channel.ChannelInitializer<io.netty.channel.Channel>() {
                    @Override
                    protected void initChannel(io.netty.channel.Channel ch) throws Exception {
                        // No handlers needed for raw TCP tunnel
                    }
                });
        
        logger.info("Connecting to backend: {}:{}", target.getHost(), target.getPort());
        bootstrap.connect(target.getHost(), target.getPort()).addListener((io.netty.channel.ChannelFutureListener) connectFuture -> {
            if (connectFuture.isSuccess()) {
                io.netty.channel.Channel backendChannel = connectFuture.channel();
                logger.info("Backend connected successfully, setting up direct relay");
                
                // Remove HTTP handlers from client channel
                try {
                    if (ctx.channel().pipeline().get("http-codec") != null) {
                        ctx.channel().pipeline().remove("http-codec");
                    }
                    if (ctx.channel().pipeline().get("http-aggregator") != null) {
                        ctx.channel().pipeline().remove("http-aggregator");
                    }
                    if (ctx.channel().pipeline().get("http1-handler") != null) {
                        ctx.channel().pipeline().remove("http1-handler");
                    }
                    
                    // Set up bidirectional relay
                    ctx.channel().pipeline().addLast(new RelayHandler(backendChannel, "client->backend"));
                    backendChannel.pipeline().addLast(new RelayHandler(ctx.channel(), "backend->client"));
                    
                    logger.info("Direct tunnel established successfully");
                } catch (Exception e) {
                    logger.error("Error setting up direct tunnel: {}", e.getMessage());
                    backendChannel.close();
                    ctx.close();
                }
            } else {
                logger.error("Failed to connect to backend: {}", connectFuture.cause().getMessage());
                ctx.close();
            }
        });
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
    
    // Inner class for raw TCP relay
    public static class RelayHandler extends ChannelInboundHandlerAdapter {
        private final Channel relayChannel;
        private final String direction;
        private static final Logger logger = LoggerFactory.getLogger(RelayHandler.class);
        
        public RelayHandler(Channel relayChannel, String direction) { 
            this.relayChannel = relayChannel;
            this.direction = direction;
        }
        
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (relayChannel.isActive()) {
                logger.info("Relaying {} data: {} bytes ({})", direction, 
                           msg instanceof io.netty.buffer.ByteBuf ? ((io.netty.buffer.ByteBuf)msg).readableBytes() : "unknown",
                           msg.getClass().getSimpleName());
                relayChannel.writeAndFlush(msg);
            } else {
                logger.warn("Cannot relay {} data - relay channel inactive", direction);
            }
        }
        
        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            logger.info("Channel inactive in {} direction", direction);
            if (relayChannel.isActive()) {
                relayChannel.close();
            }
        }
        
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            logger.error("Exception in {} relay: {}", direction, cause.getMessage());
            ctx.close();
        }
    }
}