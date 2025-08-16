package com.example.proxy.core.server.handlers;

import java.util.HashMap;
import java.util.Map;

import com.example.proxy.core.backend.BackendTarget;
import com.example.proxy.core.server.ForwardHttp2;
import com.example.proxy.core.server.ForwardRequest;

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

    @Override
    public ForwardRequest parseIncomingMessage(ChannelHandlerContext ctx, Object message) {
        if (!(message instanceof Http2StreamFrame)) {
            return null;
        }

        Http2StreamFrame streamFrame = (Http2StreamFrame) message;
        if (!(streamFrame instanceof Http2HeadersFrame)) {
            return null;
        }
        if (!(streamFrame instanceof Http2DataFrame)) {
            return null;
        }

        Http2HeadersFrame headersFrame = (Http2HeadersFrame) streamFrame;
        String method = headersFrame.headers().method().toString();
        String path = headersFrame.headers().path().toString();
        Http2Headers origHeaders = headersFrame.headers();
        Map<String, String> copyHeaders = new HashMap<>();
        for (Map.Entry<CharSequence, CharSequence> entry : origHeaders) {
            copyHeaders.put(entry.getKey().toString(), entry.getValue().toString());
        }
        int headerId = headersFrame.stream().id();

        Http2DataFrame dataFrame = (Http2DataFrame) streamFrame;
        ByteBuf data = dataFrame.content();
        int dataId = dataFrame.stream().id();

        ForwardHttp2 forwardRequest = new ForwardHttp2(data, method, path, copyHeaders, headerId, dataId);

        String acceptEncoding = copyHeaders.get("accept-encoding");
        if (acceptEncoding != null) {
            if (acceptEncoding.contains("gzip")) {
                forwardRequest.withCompression("gzip");
            } else if (acceptEncoding.contains("deflate")) {
                forwardRequest.withCompression("deflate");
            } else if (acceptEncoding.contains("br")) {
                forwardRequest.withCompression("brotli");
            }
        }
        
        String authorization = copyHeaders.get("authorization");
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
    }

    @Override
    public BackendTarget routeToBackend(ForwardRequest request) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'routeToBackend'");
    }

    @Override
    public boolean forwardToBackend(ChannelHandlerContext ctx, ForwardRequest request, BackendTarget target) {
        // TODO Auto-generated method stub
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
            ForwardRequest rqst = parseIncomingMessage(ctx, msg);
            if (rqst != null) {
                BackendTarget target = routeToBackend(rqst);
                if (target != null) {
                    boolean success = forwardToBackend(ctx, rqst, target);
                    if (!success) {
                        handleError(ctx, new Exception("Failed to forward request"), rqst);
                    }
                } else {
                    handleError(ctx, new Exception("Failed to create data container for server"), rqst);
                }
            } else {
                handleError(ctx, new Exception("Failed to parse request"), rqst);
            }
        } catch (Exception e) {

        }
    }
    
}