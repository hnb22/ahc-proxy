package com.example.proxy.core.server.handlers;

import com.example.proxy.core.server.ForwardRequest;
import com.example.proxy.core.backend.BackendTarget;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpResponse;

/* 
 *  Overview: [INTERFACE] Defines the contract for server request handlers
 *            Handles the complete request lifecycle: decode -> route -> forward -> respond
 */

public interface ServerHandler {

    ForwardRequest parseIncomingMessage(ChannelHandlerContext ctx, Object message);

    BackendTarget routeToBackend(ForwardRequest request);

    boolean forwardToBackend(ChannelHandlerContext ctx, ForwardRequest request, BackendTarget target);

    Object processBackendResponse(ChannelHandlerContext ctx, Object backendResponse);

    void sendResponseToClient(ChannelHandlerContext ctx, Object response, ForwardRequest originalRequest);

    void handleError(ChannelHandlerContext ctx, Throwable cause, ForwardRequest request);

    void cleanup(ChannelHandlerContext ctx);

    String getProtocolType();

}