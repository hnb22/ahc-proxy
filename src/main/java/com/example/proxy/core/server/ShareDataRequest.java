package com.example.proxy.core.server;

import io.netty.buffer.ByteBuf;

/* 
 * Useful interface for managing ByteBuf data copies in logging code.
 */
public interface ShareDataRequest {

    ByteBuf copyData();

    ByteBuf getOriginalData();

    ShareDataRequest retainData();

    void releaseData();

}
