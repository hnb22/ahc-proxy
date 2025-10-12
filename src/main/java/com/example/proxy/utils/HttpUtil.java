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

    public static int getPortFromURI(String uri) {
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

    public static String constructURI(String authority, String path) {
        if (authority == null) {
            return path != null ? path : "/";
        }
        
        if (authority.startsWith("http://") || authority.startsWith("https://")) {
            return authority + (path != null ? path : "/");
        }
        
        String scheme = "http://";
        return scheme + authority + (path != null ? path : "/");
    }

    public static String getHostFromAuthorityOrUri(String authority, String uri, String targetPath) {
        String host;
        
        if (authority != null) {
            if (authority.contains(":")) {
                String[] parts = authority.split(":");
                host = parts[0];
            } else {
                host = authority;
            }
        } else {
            host = HttpUtil.getHostFromURI(uri);
        }

        return host;
    }

    
    public static int getPortFromAuthorityOrUri(String authority, String uri, String targetPath) {
        int port;
        
        if (authority != null) {
            if (authority.contains(":")) {
                String[] parts = authority.split(":");
                port = Integer.parseInt(parts[1]);
            } else {
                port = 80; 
            }
        } else {
            port = HttpUtil.getPortFromURI(uri);
        }

        return port;
    }

    public static String getPathFromUri(String uri, String targetPath) {
        targetPath = HttpUtil.getPathFromURI(uri);

        return targetPath;
    }
}