package com.example.proxy.core.server;

import java.util.List;

import com.example.proxy.config.ProxyConfig;
import com.example.proxy.core.cluster.Http1ServerHandlerCluster;
import com.example.proxy.core.server.handlers.Http1ServerHandler;
import com.example.proxy.core.server.handlers.Http2ServerHandler;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2MultiplexHandler;

/* 
 *  Overview: Configures protocol pipelines for each new connection (decoders, encoders, compressors)
 */

public class ServerInitializer extends ChannelInitializer<SocketChannel> {
    
    private final String host;
    private final List<String> destinations;
    //TODO: make config immutable
    private ProxyConfig config;

    public ServerInitializer(String host, List<String> destinations) {
        this.destinations = destinations;
        this.host = host;
    }

    public String getHost() {
        return host;
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();

        if (config == null) {
            //TODO: error message
        }

        String protocol = this.config.getProtocol();
        boolean isCluster = this.config.isCluster();

        if (isCluster) {
            switch (protocol.toUpperCase()) {
                case "HTTP1":
                    configureHttp1PipelineCluster(pipeline);
                    break;
                case "HTTP2":
                    configureHttp2PipelineCluster(pipeline);
                    break;
                case "WEBSOCKET":
                    configureWebSocketPipelineCluster(pipeline);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported protocol: " + protocol);
            }
        } else {
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
    }

    private void configureWebSocketPipelineCluster(ChannelPipeline pipeline) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'configureWebSocketPipelineCluster'");
	}

	private void configureHttp2PipelineCluster(ChannelPipeline pipeline) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'configureHttp2PipelineCluster'");
	}

	private void configureHttp1PipelineCluster(ChannelPipeline pipeline) {
        pipeline.addLast("http-codec", new HttpServerCodec());
        pipeline.addLast("http-aggregator", new HttpObjectAggregator(65536));
        pipeline.addLast("http1-handler-cluster", new Http1ServerHandlerCluster(this.destinations)); 
    }

	private void configureWebSocketPipeline(ChannelPipeline pipeline) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'configureWebSocketPipeline'");
    }

    private void configureHttp2Pipeline(ChannelPipeline pipeline) {
        pipeline.addLast(Http2FrameCodecBuilder.forServer().build());
        pipeline.addLast(new Http2MultiplexHandler(new Http2ServerHandler()));
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