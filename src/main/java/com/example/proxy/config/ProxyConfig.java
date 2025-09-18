package com.example.proxy.config;


/* 
 *  Overview: Loads and stores settings for protocols
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