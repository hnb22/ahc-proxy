package com.example.proxy.core.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.proxy.config.ProxyConfig;
import com.example.proxy.exceptions.ProxyException;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

/* 
 *  Overview: Entry point for accepting TCP/HTTP/WebSocket connections, initializing pipelines,
 *            dispatching to handlers. 
 */

public class ProxyServer {

    private static final Logger logger = LoggerFactory.getLogger(ProxyServer.class);
    
    private final ProxyConfig proxyConfig;
    private ServerInitializer serverInitializer;

    public enum State {
        RUNNING,
        STOPPED
    }

    private State currentState;
    private NioEventLoopGroup bossGroup;
    private NioEventLoopGroup workerGroup;
    private ChannelFuture serverChannelFuture;

    public ProxyServer(ProxyConfig proxyConfig) {
        this.proxyConfig = proxyConfig;
        this.currentState = State.STOPPED;
    }

    public boolean isRunning() {
        return this.currentState == State.RUNNING;
    }

    public void start() throws ProxyException {
        if (this.serverInitializer == null) {
            throw new ProxyException("ServerInitializer not initialized. Call initialize() first.");
        }
        
        if (this.bossGroup == null || this.workerGroup == null) {
            throw new ProxyException("Event loop groups not initialized. Call initialize() first.");
        }

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            String host = this.serverInitializer.getHost();
            int port = this.serverInitializer.getPort();
            this.serverChannelFuture = bootstrap.group(this.bossGroup, this.workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(this.serverInitializer)
                .bind(host, port)
                .sync();

            this.currentState = State.RUNNING;
            logger.info("Proxy server started on {}:{} with protocol: {}", 
                       host, port, proxyConfig.getProtocol());
                             
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cleanup();
            throw new ProxyException("Server start interrupted", e);
        } catch (Exception e) {
            cleanup();
            throw new ProxyException("Failed to start proxy server on port " + serverInitializer.getPort(), e);
        }
    }

    private void cleanup() {
        if (this.bossGroup != null && !this.bossGroup.isShutdown()) {
            this.bossGroup.shutdownGracefully();
        }
        if (this.workerGroup != null && !this.workerGroup.isShutdown()) {
            this.workerGroup.shutdownGracefully();
        }
        this.currentState = State.STOPPED;
    }

    public void stop() {
        if (this.currentState == State.STOPPED) {
            logger.info("Proxy server is already stopped");
            return;
        }

        try {
            logger.info("Shutting down proxy server...");
            
            if (this.serverChannelFuture != null && this.serverChannelFuture.channel().isOpen()) {
                this.serverChannelFuture.channel().close().sync();
            }
            
            if (this.bossGroup != null) {
                this.bossGroup.shutdownGracefully().sync();
            }
            
            if (this.workerGroup != null) {
                this.workerGroup.shutdownGracefully().sync();
            }
            
            this.currentState = State.STOPPED;
            logger.info("Proxy server stopped successfully");
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Shutdown interrupted: {}", e.getMessage());
        } catch (Exception e) {
            logger.error("Error during shutdown: {}", e.getMessage());
        } finally {
            this.currentState = State.STOPPED;
        }
    }

    public void initialize(ServerInitializer servInt) {
        this.serverInitializer = servInt;
        this.bossGroup = new NioEventLoopGroup(1);
        this.workerGroup = new NioEventLoopGroup();
        servInt.addConfig(this.proxyConfig);
    }

    public void sync() throws ProxyException {
        try {
            if (this.serverChannelFuture != null) {
                this.serverChannelFuture.channel().closeFuture().await();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProxyException("Server sync interrupted", e);
        }
    }
}