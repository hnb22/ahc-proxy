package com.example.proxy.core.backend.custom;

import com.example.proxy.core.backend.BackendResponseCallback;
import com.example.proxy.core.server.ForwardRequest;

import io.netty.channel.ChannelHandlerContext;

public class BackendCallbackHttp1 implements BackendResponseCallback {

    private final ChannelHandlerContext clientCtx;
    private final ForwardRequest originalRequest;
    private final ResponseProcessor responseProcessor;
    
    public BackendCallbackHttp1(ChannelHandlerContext clientCtx, ForwardRequest originalRequest, ResponseProcessor responseProcessor) {
        this.clientCtx = clientCtx;
        this.originalRequest = originalRequest;
        this.responseProcessor = responseProcessor;
    }

    @Override
    public void onResponse(Object response) {
        try {
            Object processedResponse = responseProcessor.processBackendResponse(clientCtx, response);
            responseProcessor.sendResponseToClient(clientCtx, processedResponse, originalRequest);
            
        } catch (Exception e) {
            System.err.println("BackendCallbackHttp1: Error processing response: " + e.getMessage());
            responseProcessor.handleError(clientCtx, e, originalRequest);
        }
    }

    @Override
    public void onError(Throwable cause) {
        System.err.println("BackendCallbackHttp1: Backend error: " + cause.getMessage());
        responseProcessor.handleError(clientCtx, cause, originalRequest);
    }

    public ChannelHandlerContext getClientChannel() {
        return clientCtx;
    }
    
    /**
     * Interface to delegate response processing back to the handler
     */
    public interface ResponseProcessor {
        Object processBackendResponse(ChannelHandlerContext ctx, Object backendResponse);
        void sendResponseToClient(ChannelHandlerContext ctx, Object response, ForwardRequest originalRequest);
        void handleError(ChannelHandlerContext ctx, Throwable cause, ForwardRequest request);
    }
}