package com.example.proxy.core.server;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;

/**
 * A simple HTTP application server that receives requests and logs them locally.
 * This represents a backend application server that would process requests
 * forwarded from the proxy server.
 */
public class ApplicationServer {
    
    private static final Logger logger = LoggerFactory.getLogger(ApplicationServer.class);
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    
    private final String host;
    private final int port;
    private final String serverName;
    private final String logFile;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    public ApplicationServer(String host, int port, String serverName) {
        this.host = host;
        this.port = port;
        this.serverName = serverName;
        this.logFile = String.format("logs/servers/%s-requests.log", serverName);
        
        try {
            Path logDir = Paths.get("logs/servers");
            if (!Files.exists(logDir)) {
                Files.createDirectories(logDir);
            }
        } catch (Exception e) {
            logger.warn("Failed to create backend log directory: {}", e.getMessage());
        }
    }
    
    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(new HttpServerCodec());
                            pipeline.addLast(new HttpObjectAggregator(65536));
                            pipeline.addLast(new ApplicationServerHandler(serverName, logFile));
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);
            
            ChannelFuture future = bootstrap.bind(host, port).sync();
            serverChannel = future.channel();
            running.set(true);
            
            logger.info("Application server '{}' started on {}:{}", serverName, host, port);
            
            serverChannel.closeFuture().sync();
            
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
            running.set(false);
        }
    }
    
    public void stop() {
        if (running.get() && serverChannel != null) {
            logger.info("Stopping application server '{}'", serverName);
            serverChannel.close();
            running.set(false);
        }
    }
    
    public boolean isRunning() {
        return running.get();
    }
    
    /**
     * HTTP request handler for the application server
     */
    public static class ApplicationServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
        
        private final String serverName;
        private final String logFile;
        
        public ApplicationServerHandler(String serverName, String logFile) {
            this.serverName = serverName;
            this.logFile = logFile;
        }
        
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
            logRequest(request, ctx);
            
            String responseContent = createResponse(request);
            
            FullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1,
                    HttpResponseStatus.OK,
                    Unpooled.copiedBuffer(responseContent, CharsetUtil.UTF_8)
            );
            
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
            response.headers().set("X-Server", serverName);
            
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        }
        
        private void logRequest(FullHttpRequest request, ChannelHandlerContext ctx) {
            try {
                String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
                String clientAddress = ctx.channel().remoteAddress().toString();
                String method = request.method().name();
                String uri = request.uri();
                String version = request.protocolVersion().text();
                
                StringBuilder headers = new StringBuilder();
                for (String name : request.headers().names()) {
                    headers.append("\n    ").append(name).append(": ").append(request.headers().get(name));
                }
                
                String body = "";
                if (request.content().readableBytes() > 0) {
                    ByteBuf content = request.content();
                    body = content.toString(CharsetUtil.UTF_8);
                }
                
                String logEntry = String.format(
                    "[%s] REQUEST_RECEIVED on %s:\n" +
                    "  Client: %s\n" +
                    "  Method: %s %s %s\n" +
                    "  Headers:%s\n" +
                    "  Body: %s\n" +
                    "  ---",
                    timestamp, serverName, clientAddress, method, uri, version, headers.toString(), body
                );
                
                logger.info("Request received on {}: {} {} from {}", serverName, method, uri, clientAddress);
                
                writeToLogFile(logEntry);
                
            } catch (Exception e) {
                logger.error("Failed to log request on {}: {}", serverName, e.getMessage());
            }
        }
        
        private String createResponse(FullHttpRequest request) {
            return String.format(
                "{\n" +
                "  \"server\": \"%s\",\n" +
                "  \"timestamp\": \"%s\",\n" +
                "  \"method\": \"%s\",\n" +
                "  \"uri\": \"%s\",\n" +
                "  \"status\": \"success\",\n" +
                "  \"message\": \"Request processed successfully\"\n" +
                "}",
                serverName,
                LocalDateTime.now().format(TIMESTAMP_FORMAT),
                request.method().name(),
                request.uri()
            );
        }
        
        private void writeToLogFile(String logEntry) {
            try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, true))) {
                writer.println(logEntry);
            } catch (IOException e) {
                logger.warn("Failed to write to log file {}: {}", logFile, e.getMessage());
            }
        }
        
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            logger.error("Error in application server handler: {}", cause.getMessage());
            ctx.close();
        }
    }
}