package com.example.proxy.core.stages;

/* 
 *  Overview: Data container for Authorization type in HTTP requests
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