package com.example.proxy.core.pipeline.stages;
import com.example.proxy.core.pipeline.PipelineManager;

/* 
 *  Overview: Pipeline stage for authentication with HTTP/1.x, HTTP/2. and WebSocket
 */

public class AuthStage implements PipelineManager {

    final String type;

    public AuthStage(String type) {
        this.type = type;
    }
}