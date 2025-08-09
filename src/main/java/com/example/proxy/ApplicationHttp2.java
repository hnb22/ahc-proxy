package com.example.proxy;

import com.example.proxy.core.server.ProxyServer;
import com.example.proxy.config.ProtocolConfig;
import com.example.proxy.core.pipeline.stages.AuthStage;
import com.example.proxy.core.pipeline.stages.CompressionStage;
import com.example.proxy.core.server.ServerInitializer;

public class ApplicationHttp2 {

    static final int LOCAL_PORT = Integer.parseInt(System.getProperty("localPort", "8000"));

    public static void main(String[] args) {
        ProxyServer proxy = new ProxyServer(new ProtocolConfig("HTTP1", LOCAL_PORT));

        try {
            proxy.initialize(new ServerInitializer(LOCAL_PORT));
            proxy.start();

            boolean done = true;
            String data = "test";
            AuthStage auth = new AuthStage("basic");
            CompressionStage comp = new CompressionStage("gzip");
            while (done) {
                done = proxy.forward(data, "str", auth, comp, null);
            }
        } finally {
            proxy.stop();
        }
    }
}