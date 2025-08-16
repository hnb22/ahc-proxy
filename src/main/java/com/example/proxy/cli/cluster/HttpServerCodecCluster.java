package com.example.proxy.cli.cluster;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpServerCodec;

/**
 * Wrapper around HttpServerCodec for cluster functionality
 */
public class HttpServerCodecCluster extends ChannelInboundHandlerAdapter {
    
    private final HttpServerCodec codec;
    
    public HttpServerCodecCluster() {
        this.codec = new HttpServerCodec();
    }
    
    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        // Add the actual HttpServerCodec to the pipeline
        ctx.pipeline().addAfter(ctx.name(), "http-codec", codec);
        // Remove this wrapper handler
        ctx.pipeline().remove(this);
        super.channelRegistered(ctx);
    }
}
