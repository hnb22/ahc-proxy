package com.example.proxy.core.notifier;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.proxy.core.backend.BackendTarget;
import com.example.proxy.core.server.ForwardRequest;

/**
 * Service responsible for sending notifications about request forwarding activities.
 * This can be extended to support various notification mechanisms:
 */
public class NotificationService {
    
    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    
    // Dedicated notification log files
    private static final String NOTIFICATION_LOG_DIR = "logs/notifications";
    private static final String FORWARD_LOG_FILE = NOTIFICATION_LOG_DIR + "/forward-requests.log";
    private static final String RESPONSE_LOG_FILE = NOTIFICATION_LOG_DIR + "/responses.log";
    private static final String ERROR_LOG_FILE = NOTIFICATION_LOG_DIR + "/errors.log";
    
    static {
        // Create notification log directory if it doesn't exist
        try {
            Path logDir = Paths.get(NOTIFICATION_LOG_DIR);
            if (!Files.exists(logDir)) {
                Files.createDirectories(logDir);
            }
        } catch (Exception e) {
            logger.warn("Failed to create notification log directory: {}", e.getMessage());
        }
    }
    
    /**
     * Send notification about request being forwarded to a backend server
     */
    public static void notifyRequestForwarded(ForwardRequest request, BackendTarget target, String source) {
        try {
            String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
            String destination = target.getHost() + ":" + target.getPort() + target.getPath();
            String protocol = target.getMetadata().getOrDefault("protocol", "unknown");
            
            String notification = String.format(
                "[%s] FORWARD_NOTIFICATION: Source=[%s] -> Destination=[%s] Protocol=[%s]",
                timestamp, source, destination, protocol
            );
            
            // Console output for immediate visibility
            System.out.println(notification);
            
            // Log using SLF4J for structured logging
            logger.info("Request forwarded - Source: {}, Destination: {}, Protocol: {} ", 
                       source, destination, protocol);
            
            // Write to dedicated notification log file
            writeToLogFile(FORWARD_LOG_FILE, notification);
            
            // TODO: Add additional notification mechanisms
            // - sendWebSocketNotification(notification);
            // - publishToMessageQueue(notification);
            // - sendToMonitoringDashboard(notification);
            
        } catch (Exception e) {
            logger.error("Failed to send forward notification: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Send notification about successful response from backend
     */
    public static void notifyResponseReceived(BackendTarget target, String source, int statusCode) {
        try {
            String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
            String destination = target.getHost() + ":" + target.getPort();
            
            String notification = String.format(
                "[%s] RESPONSE_NOTIFICATION: Source=[%s] <- Destination=[%s] Status=[%d]",
                timestamp, source, destination, statusCode
            );
            
            System.out.println(notification);
            logger.info("Response received - Source: {}, Destination: {}, Status: {}", 
                       source, destination, statusCode);
            
            // Write to dedicated response log file
            writeToLogFile(RESPONSE_LOG_FILE, notification);
            
        } catch (Exception e) {
            logger.error("Failed to send response notification: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Send notification about errors during forwarding
     */
    public static void notifyForwardError(BackendTarget target, String source, String errorMessage) {
        try {
            String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
            String destination = target != null ? target.getHost() + ":" + target.getPort() : "unknown";
            
            String notification = String.format(
                "[%s] ERROR_NOTIFICATION: Source=[%s] -> Destination=[%s] Error=[%s]",
                timestamp, source, destination, errorMessage
            );
            
            System.err.println(notification);
            logger.error("Forward error - Source: {}, Destination: {}, Error: {}", 
                        source, destination, errorMessage);
            
            // Write to dedicated error log file
            writeToLogFile(ERROR_LOG_FILE, notification);
            
        } catch (Exception e) {
            logger.error("Failed to send error notification: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Write notification to dedicated log file
     */
    private static void writeToLogFile(String logFile, String message) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, true))) {
            writer.println(message);
        } catch (IOException e) {
            logger.warn("Failed to write to notification log file {}: {}", logFile, e.getMessage());
        }
    }
}