package examples;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.proxy.config.ProtocolConfig;
import com.example.proxy.core.server.ProxyServer;
import com.example.proxy.core.server.ServerInitializer;
import com.example.proxy.exceptions.ProxyException;

public class TestHttp2 {
    
    private static final Logger logger = LoggerFactory.getLogger(TestHttp2.class);
    private static final String LOCAL_HOST = "localhost";
    private static final int LOCAL_PORT = Integer.parseInt(System.getProperty("localPort", "8000"));

    public static void main(String[] args) throws ProxyException {
        ProxyServer proxy = new ProxyServer(new ProtocolConfig("HTTP2", LOCAL_PORT, false));

        try {
            proxy.initialize(new ServerInitializer(LOCAL_HOST, null));
            proxy.start();

            logger.info("Proxy server is running. Press Ctrl+C to stop.");

            proxy.sync();            
        } catch (ProxyException e) {
            logger.error("Failed to start proxy server: {}", e.getMessage());
            proxy.stop();
        } finally {
            logger.info("Proxy server is shutting down");
            proxy.stop();
        }
    }

}
