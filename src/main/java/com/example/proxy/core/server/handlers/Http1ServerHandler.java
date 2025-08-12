package com.example.proxy.core.server.handlers;

import java.util.HashMap;
import java.util.Map;

import com.example.proxy.core.backend.BackendTarget;
import com.example.proxy.core.server.ForwardHttp1;
import com.example.proxy.core.server.ForwardRequest;
import com.example.proxy.utils.HttpUtil;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;

/* 
 *  Overview: Handles HTTP/1.x requests including reading body, passing to routing layer
 */

public class Http1ServerHandler extends SimpleChannelInboundHandler implements ServerHandler, ChannelHandler {

    private String connectionId;
    private static final Map<String, Object> activeConnections = new HashMap<>();

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        String id = ctx.channel().id().asLongText();
        this.connectionId = String.valueOf(id.hashCode());
        activeConnections.put(connectionId, new Object());
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        cleanup(ctx);
        if (connectionId != null) {
            activeConnections.remove(connectionId);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();

        if (ctx.channel().isActive()) {
            handleError(ctx, cause, null);
        }
        ctx.close();
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
            String body = rqst.content().toString(CharsetUtil.UTF_8);

            Map<String, String> headers = new HashMap<>();
            for (Map.Entry<String, String> entry : rqst.headers()) {
                headers.put(entry.getKey().toLowerCase(), entry.getValue());
            }

            String clientAddress = ctx.channel().remoteAddress().toString();
            
            return new ForwardHttp1(body, method, uri, headers, clientAddress);

        } catch (Exception e) {
            System.err.println("Error parsing HTTP request: " + e.getMessage());
            return null;
        }
    }

    @Override
    public BackendTarget routeToBackend(ForwardRequest request) {
        try {
            if (!(request instanceof ForwardHttp1)) {
                System.err.println("Invalid request type for HTTP/1.1 handler");
                return null;
            }
            
            ForwardHttp1 rqstHttp = (ForwardHttp1) request;
            String uri = rqstHttp.getURI();
            Map<String, String> headers = rqstHttp.getHeaders();

            String host = HttpUtil.getHostFromURI(uri);
            String path = HttpUtil.getPathFromURI(uri);
            
            int port = HttpUtil.extractPortFromURI(uri);

            Map<String, String> metadata = new HashMap<>();
            metadata.put("routingRule", "path-based");
            if (headers.containsValue("text/xml")) {
                metadata.put("protocol", "http1-soap");
            } else {
                metadata.put("protocol", "http1-no-soap");
            }      
            return new BackendTarget(host, port, path, metadata);

        } catch (Exception e) {
            System.err.println("Error routing request: " + e.getMessage());
            return null;
        }
    }

    @Override
    public boolean forwardToBackend(ChannelHandlerContext ctx, ForwardRequest request, BackendTarget target) {
        try {
            if (!(request instanceof ForwardHttp1)) {
                System.err.println("Invalid request type for HTTP1");
                return false;
            }

            ForwardHttp1 httpRequest = (ForwardHttp1) request;
            String protocol = target.getMetadata().get("protocol");

            //TODO: encapsulate Handler for incoming data to delegate for additional functionality
            if ("http1-soap".equals(protocol) || "http1-no-soap".equals(protocol)) {
                System.out.println("Attempting to connect to: " + target.getHost() + ":" + target.getPort());
                
                Bootstrap client = new Bootstrap();
                client.group(ctx.channel().eventLoop())
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new HttpClientCodec());
                            ch.pipeline().addLast(new HttpObjectAggregator(65536));
                            ch.pipeline().addLast(new SimpleChannelInboundHandler<FullHttpResponse>() {
                                @Override
                                protected void channelRead0(ChannelHandlerContext backendCtx, FullHttpResponse response) throws Exception {
                                    Object processedResponse = Http1ServerHandler.this.processBackendResponse(ctx, response);
                                    Http1ServerHandler.this.sendResponseToClient(ctx, processedResponse, httpRequest);
                                    backendCtx.close();
                                }
                                
                                @Override
                                public void exceptionCaught(ChannelHandlerContext backendCtx, Throwable cause) throws Exception {
                                    System.err.println("Backend connection error: " + cause.getMessage());
                                    Http1ServerHandler.this.handleError(ctx, cause, httpRequest);
                                    backendCtx.close();
                                }
                            });
                        }
                    });
                
                try {
                    System.out.println("Connecting to: " + target.getHost() + ":" + target.getPort());
                    ChannelFuture connectFuture = client.connect(target.getHost(), target.getPort());
                    
                    // Use addListener instead of await() to avoid blocking issues
                    connectFuture.addListener((ChannelFutureListener) future -> {
                        if (future.isSuccess()) {
                            Channel clientChannel = future.channel();
                            System.out.println("Successfully connected to backend: " + target.getHost() + ":" + target.getPort());
                            System.out.println("Channel active: " + clientChannel.isActive());
                            
                            try {
                                FullHttpRequest backendRequest = new DefaultFullHttpRequest(
                                    HttpVersion.HTTP_1_1,
                                    HttpMethod.valueOf(httpRequest.getMethod()),
                                    target.getPath(),
                                    Unpooled.wrappedBuffer(httpRequest.getData().getBytes())
                                );
                                
                                httpRequest.getHeaders().forEach((key, value) -> 
                                    backendRequest.headers().set(key, value)
                                );
                                
                                backendRequest.headers().set(HttpHeaderNames.CONTENT_LENGTH, 
                                    backendRequest.content().readableBytes());
                                
                                clientChannel.writeAndFlush(backendRequest);
                                System.out.println("Request forwarded to backend");
                                
                            } catch (Exception e) {
                                System.err.println("Error sending request to backend: " + e.getMessage());
                                Http1ServerHandler.this.handleError(ctx, e, httpRequest);
                            }
                        } else {
                            System.err.println("Connection failed to " + target.getHost() + ":" + target.getPort());
                            if (future.cause() != null) {
                                System.err.println("Failure reason: " + future.cause().getMessage());
                                future.cause().printStackTrace();
                            }
                            Http1ServerHandler.this.handleError(ctx, new Exception("Connection failed"), httpRequest);
                        }
                    });
                    
                    return true;
                    
                } catch (Exception e) {
                    System.err.println("Error setting up connection to " + target.getHost() + ":" + target.getPort() + " - " + e.getMessage());
                    return false;
                }
            }

            return false;

        } catch (Exception e) {
            System.err.println("Error forwarding to backend: " + e.getMessage());
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
                        System.out.println("HTTP/1.1 response sent to client");
                    } else {
                        System.err.println("Failed to send response: " + future.cause().getMessage());
                    }
                });
            }
        } catch (Exception e) {
            System.err.println("Error sending HTTP/1.1 response: " + e.getMessage());
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
            System.err.println("Error sending HTTP/1.1 error response: " + e.getMessage());
            ctx.close();
        }    
    }

    @Override
    public void cleanup(ChannelHandlerContext ctx) {
        if (connectionId != null) {
            activeConnections.remove(connectionId);
            System.out.println("Cleaned up HTTP/1.1 connection: " + connectionId);
        }    
    }

    @Override
    public String getProtocolType() {
        return "HTTP1";
    }

    @Override
    public Object processBackendResponse(ChannelHandlerContext ctx, Object backendResponse) {
        if (!(backendResponse instanceof FullHttpResponse)) {
            return backendResponse;
        }
        
        FullHttpResponse response = (FullHttpResponse) backendResponse;
        
        // Add proxy headers
        response.headers().set("X-Proxy-Server", "ahc-proxy-http1");
        response.headers().set("X-Request-ID", connectionId);
        
        // Remove hop-by-hop headers
        response.headers().remove(HttpHeaderNames.CONNECTION);
        response.headers().remove("Proxy-Connection");
        
        System.out.println("Processed HTTP/1.1 response");
        
        return response;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
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
}