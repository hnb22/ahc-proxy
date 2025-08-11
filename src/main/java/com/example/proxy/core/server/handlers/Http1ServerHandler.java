package com.example.proxy.core.server.handlers;

import java.util.HashMap;
import java.util.Map;

import com.example.proxy.core.backend.BackendTarget;
import com.example.proxy.core.server.ForwardHttp1;
import com.example.proxy.core.server.ForwardRequest;
import com.example.proxy.utils.HttpUtil;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.util.CharsetUtil;

/* 
 *  Overview: Handles HTTP/1.x requests including reading body, passing to routing layer
 */

public class Http1ServerHandler implements ServerHandler, ChannelHandler {

    private String connectionId;
    private static final Map<String, Object> activeConnections = new HashMap<>();

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
            // TODO: Implement proper error handling
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

            String host = HttpUtil.getHostFromURI(uri);
            String path = HttpUtil.getPathFromURI(uri);

            Map<String, String> metadata = new HashMap<>();
            metadata.put("routingRule", "path-based");

            //TODO: proper ports for different services
            //TODO: metadata -> route based on REST, gRPC, WS, DB
            return new BackendTarget(host, 8080, path, metadata);

        } catch (Exception e) {
            System.err.println("Error routing request: " + e.getMessage());
            return null;
        }
    }

    @Override
    public boolean forwardToBackend(ChannelHandlerContext ctx, ForwardRequest request, BackendTarget target) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'forwardToBackend'");
    }

    @Override
    public Object processBackendResponse(ChannelHandlerContext ctx, Object backendResponse,
            ForwardRequest originalRequest) {
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
        return "HTTP1";
    }
}