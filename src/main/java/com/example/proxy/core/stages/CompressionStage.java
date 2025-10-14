package com.example.proxy.core.stages;
/* 
 *  Overview: Data container for compression with HTTP/1.x, HTTP/2. and WebSocket
 */

public class CompressionStage implements StagesManager {

    final String type;

    public CompressionStage(String type) {
        this.type = type;
    }

    public String getAlg() {
        return type;
    }
}