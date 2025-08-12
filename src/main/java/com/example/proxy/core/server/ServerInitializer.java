package com.example.proxy.core.server;

import com.example.proxy.config.ProxyConfig;
import com.example.proxy.core.server.handlers.Http1ServerHandler;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;

/* 
 *  Overview: Configures protocol pipelines for each new connection (decoders, encoders, compressors)
 */

public class ServerInitializer extends ChannelInitializer<SocketChannel> {
    
    private final int port;
    //TODO: make config immutable
    private ProxyConfig config;

    public ServerInitializer(int port) {
        this.port = port;
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();

        if (config == null) {
            //TODO: error message
        }

        String protocol = this.config.getProtocol();
        switch (protocol.toUpperCase()) {
            case "HTTP1":
                configureHttp1Pipeline(pipeline);
                break;
            case "HTTP2":
                configureHttp2Pipeline(pipeline);
                break;
            case "WEBSOCKET":
                configureWebSocketPipeline(pipeline);
                break;
            default:
                throw new IllegalArgumentException("Unsupported protocol: " + protocol);
        }
    }

    private void configureWebSocketPipeline(ChannelPipeline pipeline) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'configureWebSocketPipeline'");
    }

    private void configureHttp2Pipeline(ChannelPipeline pipeline) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'configureHttp2Pipeline'");
    }

    private void configureHttp1Pipeline(ChannelPipeline pipeline) {
        pipeline.addLast("http-codec", new HttpServerCodec());
        pipeline.addLast("http-aggregator", new HttpObjectAggregator(65536));
        pipeline.addLast("http1-handler", new Http1ServerHandler());
        
    }

    protected void addConfig(ProxyConfig config) {
        this.config = config;
    }
}