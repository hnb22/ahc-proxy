package com.example.proxy.core.server;

/**
 * Interface for protocol-specific forwarding logic.
 * Keeps ProxyServer class high-level and protocol-agnostic.
 */
public interface ProtocolForwarder {
    
    /**
     * Forward a request to the appropriate backend based on the protocol.
     * @param request The request to forward
     * @return true if forwarding was successful, false otherwise
     */
    boolean forward(ForwardRequest request);
    
    /**
     * Get the protocol type this forwarder handles.
     * @return Protocol name (HTTP1, HTTP2, WEBSOCKET, etc.)
     */
    String getProtocolType();
}
