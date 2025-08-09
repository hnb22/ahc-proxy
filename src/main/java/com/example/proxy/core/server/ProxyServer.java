package com.example.proxy.core.server;
import com.example.proxy.config.ProxyConfig;
import com.example.proxy.config.ProtocolConfig;
import com.example.proxy.core.server.handlers.ServerHandler;
import com.example.proxy.core.pipeline.stages.AuthStage;
import com.example.proxy.core.pipeline.stages.CompressionStage;
import com.example.proxy.core.pipeline.stages.CachingStage;

/* 
 *  Overview: Entry point for accepting TCP/HTTP/WebSocket connections, initializing pipelines,
 *            dispatching to handlers. 
 */

public class ProxyServer {

    private final ProxyConfig proxyConfig;

    public enum State {
        RUNNING,
        STOPPED
    }

    private State currentState;

    public ProxyServer(ProxyConfig proxyConfig) {
        this.proxyConfig = proxyConfig;
        this.currentState = State.STOPPED;
    }

    public boolean isRunning() {
        return this.currentState == State.RUNNING;
    }

    public void handler() {
        if (proxyConfig.getProtocol().equals("HTTP1")) {   
        }
    }

    public void start() {

    }

    public void stop() {

    }

    public void initialize(ServerInitializer servInt) {

    }

    public boolean forward(String data, String type, AuthStage auth, CompressionStage comp, CachingStage cache) {

    }
}