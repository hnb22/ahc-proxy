package com.example.proxy.utils;

import java.net.URI;
import java.net.URISyntaxException;

/* 
 *  Overview: Utility class for HTTP processing
 */

public final class HttpUtil {

    public static String getHostFromURI(String uriStr) {
        try {
            URI uri = new URI(uriStr);
            return uri.getHost();
        } catch (URISyntaxException e) {
            System.err.println("Error getting host from URI: " + e.getMessage());
            return null;
        }
    }

    public static String getPathFromURI(String uriStr) {
        try {
            URI uri = new URI(uriStr);
            return uri.getPath();
        } catch (URISyntaxException e) {
            System.err.println("Error getting host from URI: " + e.getMessage());
            return null;
        }
    }

    public static int extractPortFromURI(String uri) {
        try {    
            URI parsedUri = new URI(uri);
            int port = parsedUri.getPort();
            
            if (port != -1) {
                return port;
            }
            
            String scheme = parsedUri.getScheme();
            if (scheme != null) {
                switch (scheme.toLowerCase()) {
                    case "http":
                        return 80;
                    case "https":
                        return 443;
                    default:
                        return 80;
                }
            }
            
            return 80;
            
        } catch (Exception e) {
            System.err.println("Error parsing URI for port: " + uri + ", using default port 80");
            return 80;
        }
    }
}