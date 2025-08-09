package com.example.proxy.config;

/* 
 *  Overview: Holds protocol-specific settings (HTTP2 frame size, WebSocket handshake timeout)
 *            Extends ProxyConfig to inherit all global proxy settings
 */

public class ProtocolConfig extends ProxyConfig {
    
    public ProtocolConfig(String protocol, int port) {
        super(protocol, port);
        
        if (protocol.equals("HTTP1")) {
        } else if (protocol.equals("HTTP2")) {
        } else if (protocol.equals("WEBSOCKET")) {
        }
    }
}