package com.example.proxy.core.pipeline.stages;
import com.example.proxy.core.pipeline.StagesManager;

/* 
 *  Overview: Pipeline stage for authentication with HTTP/1.x, HTTP/2. and WebSocket
 */

public class AuthStage implements StagesManager {

    final String type;

    public AuthStage(String type) {
        this.type = type;
    }

    @Override
    public String getAlg() {
        return type;
    }
}