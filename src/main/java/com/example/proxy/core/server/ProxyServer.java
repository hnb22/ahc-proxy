package com.example.proxy.core.server;
import com.example.proxy.config.ProxyConfig;
import com.example.proxy.core.pipeline.stages.AuthStage;
import com.example.proxy.core.pipeline.stages.CachingStage;
import com.example.proxy.core.pipeline.stages.CompressionStage;
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

    private final ProxyConfig proxyConfig;
    //TODO: should this be immutable?
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
            this.serverChannelFuture = bootstrap.group(this.bossGroup, this.workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(this.serverInitializer)
                .bind(proxyConfig.getPort())
                .sync();

            this.currentState = State.RUNNING;
            System.out.println("Proxy server started on port " + proxyConfig.getPort() 
                             + " with protocol: " + proxyConfig.getProtocol());
                             
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cleanup();
            throw new ProxyException("Server start interrupted", e);
        } catch (Exception e) {
            cleanup();
            throw new ProxyException("Failed to start proxy server on port " + proxyConfig.getPort(), e);
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
            System.out.println("Proxy server is already stopped");
            return;
        }

        try {
            System.out.println("Shutting down proxy server...");
            
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
            System.out.println("Proxy server stopped successfully");
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Shutdown interrupted: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Error during shutdown: " + e.getMessage());
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