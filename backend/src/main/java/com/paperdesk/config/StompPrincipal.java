package com.paperdesk.config;

import java.security.Principal;

/** The authenticated user id, attached to a STOMP session at CONNECT time. */
public record StompPrincipal(long userId) implements Principal {
    @Override
    public String getName() {
        return String.valueOf(userId);
    }
}
