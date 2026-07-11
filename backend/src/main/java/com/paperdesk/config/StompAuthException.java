package com.paperdesk.config;

/**
 * Thrown from StompAuthChannelInterceptor to reject a CONNECT or SUBSCRIBE
 * frame. Spring's STOMP support turns any exception escaping a client
 * inbound-channel interceptor into a STOMP ERROR frame sent to the client,
 * then closes the session — exactly the behavior wanted here.
 */
public class StompAuthException extends RuntimeException {
    public StompAuthException(String message) {
        super(message);
    }
}
