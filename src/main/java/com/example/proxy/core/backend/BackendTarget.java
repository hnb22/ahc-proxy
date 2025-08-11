package com.example.proxy.core.backend;
public class BackendTarget {
    private final String host;
    private final int port;
    private final String path;
    private final java.util.Map<String, String> metadata;

    public BackendTarget(String host, int port, String path, java.util.Map<String, String> metadata) {
        this.host = host;
        this.port = port;
        this.path = path;
        this.metadata = metadata;
    }

    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getPath() { return path; }
    public java.util.Map<String, String> getMetadata() { return metadata; }
}