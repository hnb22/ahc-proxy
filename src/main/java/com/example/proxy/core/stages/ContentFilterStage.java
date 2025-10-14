package com.example.proxy.core.stages;

import java.net.URI;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.proxy.core.server.ForwardHttp1;
import com.example.proxy.core.server.ForwardHttp2;
import com.example.proxy.core.server.ForwardRequest;

/**
 * Content filtering stage for forward proxy functionality.
 */
public class ContentFilterStage implements StagesManager {
    
    private static final Logger logger = LoggerFactory.getLogger(ContentFilterStage.class);
    
    private static final Set<String> SOCIAL_MEDIA_DOMAINS = new HashSet<>(Arrays.asList(
        "facebook.com", "www.facebook.com",
        "twitter.com", "www.twitter.com", "x.com",
        "instagram.com", "www.instagram.com",
        "tiktok.com", "www.tiktok.com",
        "linkedin.com", "www.linkedin.com"
    ));
    
    private static final Set<String> STREAMING_DOMAINS = new HashSet<>(Arrays.asList(
        "youtube.com", "www.youtube.com",
        "netflix.com", "www.netflix.com",
        "twitch.tv", "www.twitch.tv",
        "hulu.com", "www.hulu.com"
    ));
    
    private final ContentFilterPolicy policy;
    
    public ContentFilterStage() {
        this.policy = new ContentFilterPolicy();
    }
    
    public ContentFilterStage(ContentFilterPolicy customPolicy) {
        this.policy = customPolicy;
    }
    
    @Override
    public String getAlg() {
        return "content-filter";
    }
    
    public FilterDecision evaluateRequest(ForwardRequest request) {
        try {
            String host = extractHost(request);
            String path = extractPath(request);
            String fullUrl = host + (path != null ? path : "");
            
            logger.debug("Evaluating content filter for: {}", fullUrl);
            
            FilterDecision socialMediaCheck = checkSocialMediaPolicy(host);
            if (socialMediaCheck.isBlocked()) return socialMediaCheck;
            
            FilterDecision streamingCheck = checkStreamingPolicy(host);
            if (streamingCheck.isBlocked()) return streamingCheck;
            
            FilterDecision fileTypeCheck = checkFileTypeRestrictions(path);
            if (fileTypeCheck.isBlocked()) return fileTypeCheck;
            
            logger.debug("Request allowed: {}", fullUrl);
            return FilterDecision.ALLOW;
            
        } catch (Exception e) {
            logger.error("Error in content filtering: {}", e.getMessage());
            return FilterDecision.ALLOW;
        }
    }
    
    private String extractHost(ForwardRequest request) {
        if (request instanceof ForwardHttp1) {
            ForwardHttp1 http1 = (ForwardHttp1) request;
            String hostHeader = http1.getHeaders().get("host");
            if (hostHeader != null) return hostHeader.toLowerCase();
            
            // Extract from URI
            String uri = http1.getURI();
            if (uri.startsWith("http://") || uri.startsWith("https://")) {
                try {
                    URI parsed = new URI(uri);
                    return parsed.getHost().toLowerCase();
                } catch (Exception e) {
                    logger.warn("Failed to parse URI: {}", uri);
                }
            }
        } else if (request instanceof ForwardHttp2) {
            ForwardHttp2 http2 = (ForwardHttp2) request;
            String authority = http2.getAuthority();
            if (authority != null) return authority.toLowerCase();
        }
        
        return "unknown";
    }
    
    private String extractPath(ForwardRequest request) {
        if (request instanceof ForwardHttp1) {
            ForwardHttp1 http1 = (ForwardHttp1) request;
            String uri = http1.getURI();
            try {
                URI parsed = new URI(uri);
                return parsed.getPath();
            } catch (Exception e) {
                return uri;
            }
        } else if (request instanceof ForwardHttp2) {
            ForwardHttp2 http2 = (ForwardHttp2) request;
            return http2.getPath();
        }
        
        return "";
    }
    
    private FilterDecision checkSocialMediaPolicy(String host) {
        if (!policy.isBlockSocialMediaDuringWorkHours()) {
            return FilterDecision.ALLOW;
        }
        
        if (SOCIAL_MEDIA_DOMAINS.contains(host) && isWorkHours()) {
            return FilterDecision.BLOCK("Social media blocked during work hours (9 AM - 5 PM)");
        }
        
        return FilterDecision.ALLOW;
    }
    
    private FilterDecision checkStreamingPolicy(String host) {
        if (!policy.isBlockStreamingDuringWorkHours()) {
            return FilterDecision.ALLOW;
        }
        
        if (STREAMING_DOMAINS.contains(host) && isWorkHours()) {
            return FilterDecision.BLOCK("Streaming services blocked during work hours (9 AM - 5 PM)");
        }
        
        return FilterDecision.ALLOW;
    }
    
    private FilterDecision checkFileTypeRestrictions(String path) {
        if (path == null) return FilterDecision.ALLOW;
        
        String lowerPath = path.toLowerCase();
        
        if (policy.isBlockExecutablesDuringWorkHours() && isWorkHours()) {
            if (lowerPath.endsWith(".exe") || lowerPath.endsWith(".msi") || 
                lowerPath.endsWith(".dmg") || lowerPath.endsWith(".deb")) {
                return FilterDecision.BLOCK("Executable downloads blocked during work hours");
            }
        }
        
        return FilterDecision.ALLOW;
    }
    
    private boolean isWorkHours() {
        LocalTime now = LocalTime.now();
        LocalTime workStart = LocalTime.of(9, 0);   
        LocalTime workEnd = LocalTime.of(20, 0);
        
        return now.isAfter(workStart) && now.isBefore(workEnd);
    }
    
    /**
     * Simple policy configuration class
     */
    public static class ContentFilterPolicy {
        private boolean blockSocialMediaDuringWorkHours = true;
        private boolean blockStreamingDuringWorkHours = true;
        private boolean blockAdultContent = true;
        private boolean blockExecutablesDuringWorkHours = true;
        
        public boolean isBlockSocialMediaDuringWorkHours() { return blockSocialMediaDuringWorkHours; }
        public void setBlockSocialMediaDuringWorkHours(boolean block) { this.blockSocialMediaDuringWorkHours = block; }
        
        public boolean isBlockStreamingDuringWorkHours() { return blockStreamingDuringWorkHours; }
        public void setBlockStreamingDuringWorkHours(boolean block) { this.blockStreamingDuringWorkHours = block; }
        
        public boolean isBlockAdultContent() { return blockAdultContent; }
        public void setBlockAdultContent(boolean block) { this.blockAdultContent = block; }
        
        public boolean isBlockExecutablesDuringWorkHours() { return blockExecutablesDuringWorkHours; }
        public void setBlockExecutablesDuringWorkHours(boolean block) { this.blockExecutablesDuringWorkHours = block; }
    }
    
    /**
     * Filter decision result
     */
    public static class FilterDecision {
        private final boolean blocked;
        private final String reason;
        
        private FilterDecision(boolean blocked, String reason) {
            this.blocked = blocked;
            this.reason = reason;
        }
        
        public static FilterDecision ALLOW = new FilterDecision(false, "Allowed");
        
        public static FilterDecision BLOCK(String reason) {
            return new FilterDecision(true, reason);
        }
        
        public boolean isBlocked() { return blocked; }
        public boolean isAllowed() { return !blocked; }
        public String getReason() { return reason; }
        
        @Override
        public String toString() {
            return blocked ? "BLOCKED: " + reason : "ALLOWED";
        }
    }
}
