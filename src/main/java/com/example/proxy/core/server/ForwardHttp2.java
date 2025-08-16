package com.example.proxy.core.server;

import java.util.Map;

import io.netty.buffer.ByteBuf;

public class ForwardHttp2 extends ForwardRequest {

    private final String method;
    private final String path;
    private final Map<String, String> headers;
    private final int headerId;
    private final int dataId;

    public ForwardHttp2(ByteBuf data, String method, String path, Map<String, String> headers, int headerId, int dataId) {
        super(data, "HTTP2");
        this.method = method;
        this.path = path;
        this.headerId = headerId;
        this.dataId = dataId;
        this.headers = headers;

    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public int getHeaderId() {
        return headerId;
    }

    public int getDataId() {
        return dataId;
    }
    
}
