package com.example.proxy.exceptions;
/* 
 *  Overview: Wrapper for error in Proxy processing
 */

public class ProxyException extends Exception {

    public ProxyException() {
        super();
    }

    public ProxyException(String message) {
        super(message);
    }

    public ProxyException(String message, Throwable cause) {
        super(message, cause);
    }

    public ProxyException(Throwable cause) {
        super(cause);
    }
}