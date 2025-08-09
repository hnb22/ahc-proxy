package com.example.proxy.core.server;

/* 
 *  Overview: Configures protocol pipelines for each new connection (decoders, encoders, compressors)
 */

public class ServerInitializer {
    
    private final int port;

    public ServerInitializer(int port) {
        this.port = port;
    }
}