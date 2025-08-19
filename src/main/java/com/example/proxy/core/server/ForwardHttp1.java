package com.example.proxy.core.server;

import java.util.Map;

import com.example.proxy.core.cluster.ShareDataRequest;

import io.netty.buffer.ByteBuf;

public class ForwardHttp1 extends ForwardRequest implements ShareDataRequest{
    
    private final String method;
    private final String uri;
    private final Map<String, String> headers;
    private final String clientAddress;

    public ForwardHttp1(ByteBuf data, String method, String uri, Map<String, String> headers, String clientAddress) {
        super(data, "HTTP1");
        this.method = method;
        this.uri = uri;
        this.headers = headers;
        this.clientAddress = clientAddress;
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

    public String getMethod() {
        return method;
    }

    @Override
    public ByteBuf copyData() {
        return getData().copy();
    }

    @Override
    public ByteBuf getOriginalData() {
        return getData();
    }

    @Override
    public ShareDataRequest retainData() {
        getData().retain();
        return this;
    }

    @Override
    public void releaseData() {
        getData().release();
    }
}