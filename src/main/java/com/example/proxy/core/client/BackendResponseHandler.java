package com.example.proxy.core.client;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;

/**
 * Handles responses from backend servers in the client-side pipeline.
 * This is the wrapper that processes responses after HttpBackendClient sends requests to backend servers.
 */
public class BackendResponseHandler extends SimpleChannelInboundHandler<FullHttpResponse> {
    
    private final BackendResponseCallback callback;
    
    public BackendResponseHandler() {
        this.callback = null;
    }
    
    public BackendResponseHandler(BackendResponseCallback callback) {
        this.callback = callback;
    }
    
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse response) throws Exception {
        
        if (callback != null) {
            callback.onResponse(response);
        } else {
        }
        
        ctx.close();
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        System.err.println("BackendResponseHandler: Error receiving response from backend: " + cause.getMessage());
        
        if (callback != null) {
            callback.onError(cause);
        } else {
            cause.printStackTrace(System.err);
        }
        
        ctx.close();
    }
}
