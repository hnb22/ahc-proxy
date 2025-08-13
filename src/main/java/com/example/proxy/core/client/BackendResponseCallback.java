package com.example.proxy.core.client;

/**
 * Callback interface for handling backend responses and errors
 */
public interface BackendResponseCallback {
    void onResponse(Object response);
    void onError(Throwable cause);
}
