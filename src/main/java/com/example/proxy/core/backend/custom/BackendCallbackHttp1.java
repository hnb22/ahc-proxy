package com.example.proxy.core.backend.custom;

import com.example.proxy.core.backend.BackendResponseCallback;
import com.example.proxy.core.backend.BackendTarget;
import com.example.proxy.core.notifier.NotificationService;
import com.example.proxy.core.server.ForwardRequest;

import io.netty.channel.ChannelHandlerContext;

public class BackendCallbackHttp1 implements BackendResponseCallback {

    private final ChannelHandlerContext clientCtx;
    private final ForwardRequest originalRequest;
    private final ResponseProcessor responseProcessor;
    private final BackendTarget backendTarget; // Add backend target information
    
    public BackendCallbackHttp1(ChannelHandlerContext clientCtx, ForwardRequest originalRequest, ResponseProcessor responseProcessor) {
        this.clientCtx = clientCtx;
        this.originalRequest = originalRequest;
        this.responseProcessor = responseProcessor;
        this.backendTarget = null; // For backward compatibility
    }
    
    public BackendCallbackHttp1(ChannelHandlerContext clientCtx, ForwardRequest originalRequest, ResponseProcessor responseProcessor, BackendTarget backendTarget) {
        this.clientCtx = clientCtx;
        this.originalRequest = originalRequest;
        this.responseProcessor = responseProcessor;
        this.backendTarget = backendTarget;
    }

    @Override
    public void onResponse(Object response) {
        try {
            // Send notification about where the request was forwarded
            sendNotification(response);
            
            Object processedResponse = responseProcessor.processBackendResponse(clientCtx, response);
            responseProcessor.sendResponseToClient(clientCtx, processedResponse, originalRequest);
            
        } catch (Exception e) {
            System.err.println("BackendCallbackHttp1: Error processing response: " + e.getMessage());
            responseProcessor.handleError(clientCtx, e, originalRequest);
        }
    }
    
    /**
     * Send notification about request forwarding destination
     */
    private void sendNotification(Object response) {
        try {
            if (backendTarget != null) {
                String source = clientCtx.channel().remoteAddress().toString();
                
                // Notify about successful forwarding
                NotificationService.notifyRequestForwarded(originalRequest, backendTarget, source);
                
                // If we have response information, notify about that too
                if (response instanceof io.netty.handler.codec.http.FullHttpResponse) {
                    io.netty.handler.codec.http.FullHttpResponse httpResponse = 
                        (io.netty.handler.codec.http.FullHttpResponse) response;
                    NotificationService.notifyResponseReceived(backendTarget, source, httpResponse.status().code());
                }
                
            } else {
                System.out.println("NOTIFICATION: Request forwarded to backend server (destination unknown)");
            }
        } catch (Exception e) {
            System.err.println("Failed to send notification: " + e.getMessage());
        }
    }

    @Override
    public void onError(Throwable cause) {
        System.err.println("BackendCallbackHttp1: Backend error: " + cause.getMessage());
        
        // Send error notification
        if (backendTarget != null) {
            String source = clientCtx.channel().remoteAddress().toString();
            NotificationService.notifyForwardError(backendTarget, source, cause.getMessage());
        }
        
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