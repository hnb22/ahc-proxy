package com.example.proxy.core.server;

import java.util.Map;

import com.example.proxy.utils.HttpUtil;

import io.netty.buffer.ByteBuf;

public class ForwardHttp2 extends ForwardRequest {

    private final String method;
    private final String path;
    private final String authority;
    private final String uri;
    private final Map<String, String> headers;
    private final int headerId;
    private final int dataId;
    private boolean body;

    public ForwardHttp2(ByteBuf data, String method, String path, String authority, Map<String, String> headers, int headerId, int dataId, boolean body) {
        super(data, "HTTP/2");
        this.method = method;
        this.path = path;
        this.authority = authority;
        this.headerId = headerId;
        this.dataId = dataId;
        this.headers = headers;
        
        this.uri = HttpUtil.constructURI(authority, path);
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }
    
    public String getAuthority() {
        return authority;
    }
    
    public String getURI() {
        return uri;
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

    public boolean isBody() {
        return body;
    }
    
}
