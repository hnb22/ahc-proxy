package com.example.proxy.core.server;

import io.netty.buffer.ByteBuf;

public interface ShareDataRequest {

    ByteBuf copyData();

    ByteBuf getOriginalData();

    ShareDataRequest retainData();

    void releaseData();

}
