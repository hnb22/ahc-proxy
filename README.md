# AHC Proxy Server

A HTTP/HTTPS forward proxy server built with Java and Netty, featuring remote server logging.

#### Regular Proxy Mode
```java
package examples;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.proxy.config.ProxyConfig;
import com.example.proxy.core.server.ProxyServer;
import com.example.proxy.core.server.ServerInitializer;
import com.example.proxy.core.server.ServerInitializer.Notifier;
import com.example.proxy.exceptions.ProxyException;

public class TestHttp1 {

    private static final Logger logger = LoggerFactory.getLogger(TestHttp1.class);
    
    static final int LOCAL_PORT = Integer.parseInt(System.getProperty("localPort", "8000"));
    static final String LOCAL_HOST = "localhost";

    public static void main(String[] args) throws ProxyException {
        ProxyServer proxy = new ProxyServer(new ProxyConfig("HTTP/1.1"));

        try {
            proxy.initialize(new ServerInitializer(LOCAL_HOST, LOCAL_PORT, Notifier.NO));
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
```

#### Notifier Mode
```java
package examples;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.proxy.config.ProxyConfig;
import com.example.proxy.core.server.ProxyServer;
import com.example.proxy.core.server.ServerInitializer;
import com.example.proxy.core.server.ServerInitializer.Notifier;
import com.example.proxy.exceptions.ProxyException;

public class TestHttp1Notifier {

    private static final Logger logger = LoggerFactory.getLogger(TestHttp1Notifier.class);
    private static final int LOCAL_PORT = Integer.parseInt(System.getProperty("localPort", "8000"));
    private static final String LOCAL_HOST = "localhost";
    private static final List<String> loggingDestinations = List.of("localhost:8001", "localhost:8002", "localhost:8003");

    public static void main(String[] args) throws ProxyException {
        ProxyServer proxy = new ProxyServer(new ProxyConfig("HTTP/1.1"));

        try {
            proxy.initialize(new ServerInitializer(LOCAL_HOST, LOCAL_PORT, Notifier.YES, loggingDestinations));
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
```
