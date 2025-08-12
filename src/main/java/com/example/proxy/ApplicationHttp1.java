package com.example.proxy;

import com.example.proxy.config.ProtocolConfig;
import com.example.proxy.core.server.ProxyServer;
import com.example.proxy.core.server.ServerInitializer;
import com.example.proxy.exceptions.ProxyException;

public class ApplicationHttp1 {

    static final int LOCAL_PORT = Integer.parseInt(System.getProperty("localPort", "8000"));

    public static void main(String[] args) throws ProxyException {
        ProxyServer proxy = new ProxyServer(new ProtocolConfig("HTTP1", LOCAL_PORT));

        try {
            proxy.initialize(new ServerInitializer(LOCAL_PORT));
            proxy.start();
            
            System.out.println("Proxy server is running. Press Ctrl+C to stop.");
            
            proxy.sync();
            
        } catch (ProxyException e) {
            System.err.println("Failed to start proxy server: " + e.getMessage());
            proxy.stop();
        } finally {
            System.out.println("Proxy server is shuting down");
            proxy.stop();
        }
    }
}