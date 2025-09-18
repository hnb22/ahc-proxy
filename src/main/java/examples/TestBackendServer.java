package examples;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.proxy.core.server.ApplicationServer;

/**
 * Launcher for backend application servers.
 * These servers receive HTTP requests and log them locally.
 */
public class TestBackendServer {
    
    private static final Logger logger = LoggerFactory.getLogger(TestBackendServer.class);
    
    static final int LOCAL_PORT = Integer.parseInt(System.getProperty("localPort", "8001"));
    static final String LOCAL_HOST = "localhost";
    static final String SERVER_NAME = System.getProperty("serverName", "backend-" + LOCAL_PORT);
    
    public static void main(String[] args) {
        ApplicationServer server = new ApplicationServer(LOCAL_HOST, LOCAL_PORT, SERVER_NAME);
        
        // Add shutdown hook for graceful stop
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down backend server '{}'", SERVER_NAME);
            server.stop();
        }));
        
        try {
            logger.info("Starting backend application server '{}' on {}:{}", SERVER_NAME, LOCAL_HOST, LOCAL_PORT);
            server.start(); // This blocks until server is stopped
            
        } catch (InterruptedException e) {
            logger.info("Backend server '{}' was interrupted", SERVER_NAME);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("Failed to start backend server '{}': {}", SERVER_NAME, e.getMessage(), e);
        } finally {
            logger.info("Backend server '{}' has stopped", SERVER_NAME);
        }
    }
}