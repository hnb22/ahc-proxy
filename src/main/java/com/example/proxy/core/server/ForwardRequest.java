package com.example.proxy.core.server;

import java.util.ArrayList;
import java.util.List;

import com.example.proxy.core.stages.AuthStage;
import com.example.proxy.core.stages.CompressionStage;
import com.example.proxy.core.stages.StagesManager;

import io.netty.buffer.ByteBuf;

/**
 * Builder pattern for creating forward requests with optional pipeline stages.
 * Provides a fluent, readable API for configuring which stages to apply.
 * 
 * IMPROVED OOP Design:
 * - Composition: Uses List<StagesManager> for multiple stages
 * - Builder Pattern: Fluent method chaining
 * - Encapsulation: Private fields with controlled access
 * - Polymorphism: Different stage implementations through common interface
 */
public class ForwardRequest {
    private final ByteBuf data;
    private final String type;
    private final List<StagesManager> stages;

    protected ForwardRequest(ByteBuf data, String type) {
        this.data = data;
        this.type = type;
        this.stages = new ArrayList<>();
    }

    public static ForwardRequest of(ByteBuf data, String type) {
        return new ForwardRequest(data, type);
    }

    public ForwardRequest withAuth(String authType) {
        this.stages.add(new AuthStage(authType));
        return this;
    }

    public ForwardRequest withCompression(String compressionType) {
        this.stages.add(new CompressionStage(compressionType));
        return this;
    }

    public ByteBuf getData() { return data; }
    public String getType() { return type; }
    public List<StagesManager> getStages() { return new ArrayList<>(stages); }  // Defensive copy

    public boolean hasStages() { return !stages.isEmpty(); }
    
    public boolean hasAuth() { 
        return stages.stream().anyMatch(stage -> stage instanceof AuthStage); 
    }
    
    public boolean hasCompression() { 
        return stages.stream().anyMatch(stage -> stage instanceof CompressionStage); 
    }
    
    public AuthStage getAuthStage() {
        return (AuthStage) stages.stream()
            .filter(stage -> stage instanceof AuthStage)
            .findFirst()
            .orElse(null);
    }
    
    public CompressionStage getCompressionStage() {
        return (CompressionStage) stages.stream()
            .filter(stage -> stage instanceof CompressionStage)
            .findFirst()
            .orElse(null);
    }
}
