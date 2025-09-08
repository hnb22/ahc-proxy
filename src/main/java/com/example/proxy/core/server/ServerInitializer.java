package com.example.proxy.core.server;

import java.util.List;

import com.example.proxy.config.ProxyConfig;
import com.example.proxy.core.notifier.NotifierHttp1ServerHandler;
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
    
    //TODO: make config immutable
    private ProxyConfig config;
    private List<String> destinations;
    private Notifier isNotifier;

    private String host;
    private int port;

    public ServerInitializer(String host, int port, Notifier isNotifier, List<String> destinations) {
        this.destinations = destinations;
        this.isNotifier = isNotifier;
        
        this.host = host;
        this.port = port;
    }

    public ServerInitializer(String host, int port, Notifier isNotifier) {
        this.isNotifier = isNotifier;

        this.host = host;
        this.port = port;
    }

    public enum Notifier {
        YES,
        NO
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();

        if (config == null) {
            //TODO: error message
        }

        String protocol = this.config.getProtocol();
        Notifier isNotifier = this.isNotifier;

        if (isNotifier == isNotifier.YES) {
            switch (protocol.toUpperCase()) {
                case "HTTP/1.1":
                    configureHttp1PipelineCluster(pipeline);
                    break;
                case "HTTP/2":
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
                case "HTTP/1.1":
                    configureHttp1Pipeline(pipeline);
                    break;
                case "HTTP/2":
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
        pipeline.addLast("http1-handler-cluster", new NotifierHttp1ServerHandler(this.destinations)); 
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

    public void addConfig(ProxyConfig config) {
        this.config = config;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

}