package com.example.proxy.core.server;

import java.util.Map;

public class ForwardHttp1 extends ForwardRequest {
    
    private final String method;
    private final String uri;
    private final Map<String, String> headers;
    private final String clientAddress;

    public ForwardHttp1(String data, String method, String uri, Map<String, String> headers, String clientAddress) {
        super(data, "HTTP1");
        this.method = method;
        this.uri = uri;
        this.headers = headers;
        this.clientAddress = clientAddress;
    }

    public String getMethod() {
        return method;
    }
    
    public Map<String, String> getHeaders() {
        return headers;
    }

    public String getClientAddress() {
        return clientAddress;
    }

    public String getURI() {
        return uri;
    }
}