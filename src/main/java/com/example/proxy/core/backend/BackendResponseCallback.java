package com.example.proxy.core.backend;

import io.netty.channel.Channel;
import java.util.Queue;

/**
 * Callback interface for handling backend responses and errors
 */
public interface BackendResponseCallback {
    void onResponse(Object response);
    void onError(Throwable cause);
    

    default void onConnected(Channel backendChannel, Queue<Object> buffer, Channel clientChannel) {}
}
