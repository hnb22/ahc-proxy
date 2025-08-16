package com.example.proxy.cli.cluster;

import io.netty.buffer.ByteBuf;

public interface ShareDataRequest {
    
    /**
     * Creates a copy of the request data for sharing with multiple destinations
     * @return A new ByteBuf copy that can be independently consumed
     */
    ByteBuf copyData();
    
    /**
     * Gets the original data (use with caution - may affect reference counting)
     * @return The original ByteBuf
     */
    ByteBuf getOriginalData();
    
    /**
     * Retains the original data for sharing (increments reference count)
     * @return This request for method chaining
     */
    ShareDataRequest retainData();
    
    /**
     * Releases the data when done (decrements reference count)
     */
    void releaseData();
}
