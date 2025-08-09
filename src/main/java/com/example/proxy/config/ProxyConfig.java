package com.example.proxy.config;


/* 
 *  Overview: Loads and stores settings for ports, protocols (HTTP1/HTTP2,WebSocket),
 *            max connections, timeout, SS, routing rules, backend targets
 *            (Base class containing all global proxy settings)
 */

public class ProxyConfig {
    
    // Global proxy settings
    protected String protocol;
    protected int port;
    protected boolean sslEnabled;
    protected int maxConnections;
    protected int timeoutMs;
    
    public ProxyConfig(String protocol, int port) {
        this.protocol = protocol;
        this.port = port;
        this.sslEnabled = false;
        this.maxConnections = 1000;
        this.timeoutMs = 30000;
    }
    
    public String getProtocol() {
        return protocol;
    }
    
    public int getPort() {
        return port;
    }
    
    public boolean isSslEnabled() {
        return sslEnabled;
    }
    
    public int getMaxConnections() {
        return maxConnections;
    }
    
    public int getTimeoutMs() {
        return timeoutMs;
    }
    
    public void setSslEnabled(boolean sslEnabled) {
        this.sslEnabled = sslEnabled;
    }
    
    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }
}