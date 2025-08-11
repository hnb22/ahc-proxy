package com.example.proxy.core.server;

import com.example.proxy.core.pipeline.stages.AuthStage;
import com.example.proxy.core.pipeline.stages.CachingStage;
import com.example.proxy.core.pipeline.stages.CompressionStage;

/**
 * Builder pattern for creating forward requests with optional pipeline stages.
 * Provides a fluent, readable API for configuring which stages to apply.
 */

public class ForwardRequest {
    private final String data;
    private final String type;
    private AuthStage authStage;
    private CompressionStage compressionStage;
    private CachingStage cachingStage;

    protected ForwardRequest(String data, String type) {
        this.data = data;
        this.type = type;
    }

    public static ForwardRequest of(String data, String type) {
        return new ForwardRequest(data, type);
    }

    public ForwardRequest withAuth(String authType) {
        this.authStage = new AuthStage(authType);
        return this;
    }

    public ForwardRequest withAuth(AuthStage authStage) {
        this.authStage = authStage;
        return this;
    }

    public ForwardRequest withCompression(String compressionType) {
        this.compressionStage = new CompressionStage(compressionType);
        return this;
    }

    public ForwardRequest withCompression(CompressionStage compressionStage) {
        this.compressionStage = compressionStage;
        return this;
    }

    public ForwardRequest withCaching() {
        this.cachingStage = new CachingStage();
        return this;
    }

    public ForwardRequest withCaching(CachingStage cachingStage) {
        this.cachingStage = cachingStage;
        return this;
    }

    public String getData() { return data; }
    public String getType() { return type; }
    public AuthStage getAuthStage() { return authStage; }
    public CompressionStage getCompressionStage() { return compressionStage; }
    public CachingStage getCachingStage() { return cachingStage; }

    public boolean hasAuth() { return authStage != null; }
    public boolean hasCompression() { return compressionStage != null; }
    public boolean hasCaching() { return cachingStage != null; }
}
