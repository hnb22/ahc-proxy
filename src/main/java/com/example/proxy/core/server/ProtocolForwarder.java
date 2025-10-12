package com.example.proxy.core.server;

/**
 * Interface for protocol-specific forwarding logic.
 */
public interface ProtocolForwarder {
    

    boolean forward(ForwardRequest request);

    String getProtocolType();
}
