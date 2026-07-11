package com.paperdesk.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.support.AbstractSubscribableChannel;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A focused unit test can prove StompAuthChannelInterceptor's logic is
 * correct without proving it's actually wired into the live message
 * pipeline. This loads the real Spring context and checks the interceptor
 * is registered on the channel Spring actually dispatches inbound STOMP
 * frames through — closing that gap.
 */
@SpringBootTest
@ActiveProfiles("test")
class WebSocketAuthWiringTest {

    @Autowired
    @Qualifier("clientInboundChannel")
    AbstractSubscribableChannel clientInboundChannel;

    @Test
    void authInterceptorIsRegisteredOnTheClientInboundChannel() {
        boolean registered = clientInboundChannel.getInterceptors().stream()
                .anyMatch(i -> i instanceof StompAuthChannelInterceptor);
        assertTrue(registered, "StompAuthChannelInterceptor must be registered on clientInboundChannel");
    }
}
