package com.example.proxy.core.backend;

import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.proxy.core.server.ForwardHttp1;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

/**
 * Dedicated HTTP client for forwarding requests to backend servers.
 * Encapsulates all connection logic and provides callbacks for response handling.
 */
public class HttpBackendClient {
    
    private static final Logger logger = LoggerFactory.getLogger(HttpBackendClient.class);
    
    private final EventLoopGroup eventLoopGroup;
    private final String auth;
    private final String compression;
    
    public HttpBackendClient(EventLoopGroup eventLoopGroup, String auth, String compression) {
        this.eventLoopGroup = eventLoopGroup;
        this.auth = auth;
        this.compression = compression;
    }
    
    public CompletableFuture<Boolean> forwardRequestHTTP(ForwardHttp1 request, BackendTarget target, BackendResponseCallback callback) {
        CompletableFuture<Boolean> resultFuture = new CompletableFuture<>();
        
        try {
            Bootstrap client = createBootstrapHttp(callback);
            
            logger.info("Connecting to: {}:{}", target.getHost(), target.getPort());
            ChannelFuture connectFuture = client.connect(target.getHost(), target.getPort());
            
            connectFuture.addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    handleSuccessfulConnection(future.channel(), request, target, resultFuture);
                } else {
                    handleConnectionFailure(target, future.cause(), callback, resultFuture);
                }
            });
            
        } catch (Exception e) {
            logger.error("Error setting up connection to {}:{} - {}", target.getHost(), target.getPort(), e.getMessage());
            callback.onError(e);
            resultFuture.complete(false);
        }
        
        return resultFuture;
    }
    
    private Bootstrap createBootstrapHttp(BackendResponseCallback callback) {
        Bootstrap client = new Bootstrap();
        client.group(eventLoopGroup)
            .channel(NioSocketChannel.class)
            .handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) throws Exception {
                    buildPipeline(ch, callback);
                }
            });
        return client;
    }
    
    private void buildPipeline(SocketChannel ch, BackendResponseCallback callback) throws Exception {
        if ("ssl".equals(auth) || "tls".equals(auth)) {
            SslContext sslContext = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .build();
            ch.pipeline().addLast("ssl", sslContext.newHandler(ch.alloc()));
            logger.info("Request encrypted by {}", auth);
        }
        
        ch.pipeline().addLast("http-codec", new HttpClientCodec());
        
        if (!"none".equals(compression)) {
            ch.pipeline().addLast("decompressor", new HttpContentDecompressor());
            logger.info("Request decompressed by {}", compression);
        }
        
        ch.pipeline().addLast("http-aggregator", new HttpObjectAggregator(65536));
        
        ch.pipeline().addLast("backend-response", new BackendResponseHandler(callback));
    }
    
    private void handleSuccessfulConnection(Channel clientChannel, ForwardHttp1 request, BackendTarget target, CompletableFuture<Boolean> resultFuture) {
        try {
            FullHttpRequest backendRequest = createBackendRequest(request, target);
            
            clientChannel.writeAndFlush(backendRequest).addListener(future -> {
                if (future.isSuccess()) {
                    resultFuture.complete(true);
                } else {
                    resultFuture.complete(false);
                }
            });

            logger.info("Successfully send data back to client.");
            
        } catch (Exception e) {
            logger.error("Error sending request to backend: {}", e.getMessage());
            resultFuture.complete(false);
        }
    }
    
    private void handleConnectionFailure(BackendTarget target, Throwable cause, BackendResponseCallback callback, CompletableFuture<Boolean> resultFuture) {
        logger.error("Connection failed to {}:{}", target.getHost(), target.getPort());
        if (cause != null) {
            logger.error("Failure reason: {}", cause.getMessage(), cause);
        }
        callback.onError(new Exception("Connection failed", cause));
        resultFuture.complete(false);
    }
    
    private FullHttpRequest createBackendRequest(ForwardHttp1 request, BackendTarget target) {
        // Retain the original ByteBuf to increment its reference count
        // This prevents the "refCnt: 0" error when the HTTP encoder releases it
        ByteBuf originalData = request.getData().retain();
        
        FullHttpRequest backendRequest = new DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1,
            HttpMethod.valueOf(request.getMethod()),
            target.getPath(),
            originalData
        );
        
        request.getHeaders().forEach((key, value) -> 
            backendRequest.headers().set(key, value)
        );
        
        applyAuthHeaders(backendRequest);
        
        applyCompressionHeaders(backendRequest);
                
        backendRequest.headers().set(HttpHeaderNames.CONTENT_LENGTH, 
            backendRequest.content().readableBytes());
        
        return backendRequest;
    }
    
    /**
     * Apply authentication headers to the outgoing request
     */
    private void applyAuthHeaders(FullHttpRequest request) {
        if ("none".equals(auth)) return;
        
        switch (auth.toLowerCase()) {
            case "bearer":
                request.headers().set("Authorization", "Bearer <token>");
                request.headers().set("X-Auth-Client", "ahc-proxy");
                break;
            case "basic":
                request.headers().set("Authorization", "Basic <credentials>");
                request.headers().set("X-Auth-Client", "ahc-proxy");
                break;
            case "oauth":
                request.headers().set("Authorization", "OAuth <token>");
                request.headers().set("X-OAuth-Client", "ahc-proxy");
                break;
            case "jwt":
                request.headers().set("Authorization", "Bearer <jwt-token>");
                request.headers().set("X-JWT-Client", "ahc-proxy");
                break;
            default:
                request.headers().set("X-Auth-Type", auth);
                break;
        }
        
        request.headers().set("X-Auth-Applied", "true");
    }
    
    /**
     * Apply compression headers to the outgoing request
     */
    private void applyCompressionHeaders(FullHttpRequest request) {
        if ("none".equals(compression)) return;
        
        switch (compression.toLowerCase()) {
            case "gzip":
                request.headers().set("Accept-Encoding", "gzip");
                request.headers().set("X-Compression-Preferred", "gzip");
                break;
            case "deflate":
                request.headers().set("Accept-Encoding", "deflate");
                request.headers().set("X-Compression-Preferred", "deflate");
                logger.debug("Applied deflate compression headers");
                break;
            case "br":
                request.headers().set("Accept-Encoding", "br");
                request.headers().set("X-Compression-Preferred", "br");
                logger.debug("Applied Brotli compression headers");
                break;
            default:
                request.headers().set("Accept-Encoding", compression);
                request.headers().set("X-Compression-Preferred", compression);
                break;
        }
        
        request.headers().set("X-Compression-Applied", "true");
    }

    public CompletableFuture<Boolean> forwardRequestHTTPS(ChannelHandlerContext ctx, ForwardHttp1 httpRequest, BackendTarget target,
            BackendResponseCallback callback) {

        Channel clientChannel = ctx.channel();

        // Remove HTTP handlers
        if (clientChannel.pipeline().get("http-codec") != null) {
            clientChannel.pipeline().remove("http-codec");
        }
        if (clientChannel.pipeline().get("http-aggregator") != null) {
            clientChannel.pipeline().remove("http-aggregator");
        }
        if (clientChannel.pipeline().get("http1-handler") != null) {
            clientChannel.pipeline().remove("http1-handler");
        }

        CompletableFuture<Boolean> resultFuture = new CompletableFuture<>();

        // Buffer client data until backend is ready
        final java.util.Queue<Object> buffer = new java.util.concurrent.ConcurrentLinkedQueue<>();
        ChannelInboundHandlerAdapter bufferHandler = new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                buffer.add(msg);
            }
        };
        clientChannel.pipeline().addLast("buffer-handler", bufferHandler);

        try {
            Bootstrap backendClient = createBootstrapHttps();
            logger.info("Connecting to: {}:{}", target.getHost(), target.getPort());
            ChannelFuture connectFuture = backendClient.connect(target.getHost(), target.getPort());

            connectFuture.addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    Channel backendChannel = future.channel();
                    // Flush buffered client data to backend
                    while (!buffer.isEmpty()) {
                        Object msg = buffer.poll();
                        if (msg != null) {
                            backendChannel.writeAndFlush(msg);
                        }
                    }
                    clientChannel.pipeline().remove("buffer-handler");
                    clientChannel.pipeline().addLast(new RelayHandler(backendChannel));
                    backendChannel.pipeline().addLast(new RelayHandler(clientChannel));
                    resultFuture.complete(true);
                } else {
                    handleConnectionFailure(target, future.cause(), callback, resultFuture);
                }
            });
        } catch (Exception e) {
            logger.error("Error setting up connection to {}:{} - {}", target.getHost(), target.getPort(), e.getMessage());
            callback.onError(e);
            resultFuture.complete(false);
        }
        return resultFuture;
    }

    public static class RelayHandler extends ChannelInboundHandlerAdapter {

        private final Channel relayChannel;
        public RelayHandler(Channel relayChannel) { this.relayChannel = relayChannel; }
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (relayChannel.isActive()) {
                relayChannel.writeAndFlush(msg);
            }
        }
        @Override
        public void channelInactive(io.netty.channel.ChannelHandlerContext ctx) {
            if (relayChannel.isActive()) {
                relayChannel.close();
            }
        }
        @Override
        public void exceptionCaught(io.netty.channel.ChannelHandlerContext ctx, Throwable cause) {
            ctx.close();
        }
    }

    private Bootstrap createBootstrapHttps() {
        Bootstrap client = new Bootstrap();
        client.group(eventLoopGroup)
            .channel(NioSocketChannel.class)
            .handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) throws Exception {}
            });
        return client;
    }
    
}
