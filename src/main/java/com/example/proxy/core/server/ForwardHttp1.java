package com.example.proxy.core.server;

import java.lang.reflect.Method;
import java.net.http.HttpClient.Version;
import java.util.Map;

import com.example.proxy.core.backend.BackendTarget;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;

public class ForwardHttp1 extends ForwardRequest {
    
    private final String method;
    private final String uri;
    private final Map<String, String> headers;
    private final String clientAddress;
    private Bootstrap client;
    private Channel clientChannel;
    private FullHttpRequest backendRequest;

    public ForwardHttp1(String data, String method, String uri, Map<String, String> headers, String clientAddress) {
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
}