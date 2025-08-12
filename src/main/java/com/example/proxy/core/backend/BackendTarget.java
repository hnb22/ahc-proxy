package com.example.proxy.core.backend;

import java.util.Map;

public class BackendTarget {
    
    private String host;
    private int port;
    private String path;
    private Map<String, String> metadata;

    public BackendTarget(String host, int port, String path, Map<String, String> metadata) {
        this.host = host;
        this.port = port;
        this.path = path;
        this.metadata = metadata;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getPath() {
        return path;
    }
}
