package com.example.proxy.config;


/* 
 *  Overview: Loads and stores settings for ports, protocols (HTTP1/HTTP2,WebSocket),
 *            max connections, timeout, SS, routing rules, backend targets
 *            (Base class containing all global proxy settings)
 */

public class ProxyConfig {
    
    private String protocol;
    
    public ProxyConfig(String protocol) {
        this.protocol = protocol;
    }

    public String getProtocol() {
        return protocol;
    }
}