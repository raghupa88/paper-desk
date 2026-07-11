package com.paperdesk.config;

import com.paperdesk.domain.Account;
import com.paperdesk.repo.AccountRepo;
import io.jsonwebtoken.Claims;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * STOMP frames travel over a separate message pipeline from the servlet
 * filter chain — JwtAuthFilter never sees them, and the /ws HTTP upgrade
 * handshake is intentionally left unauthenticated (a browser WebSocket
 * handshake can't carry a custom Authorization header). Without this
 * interceptor, any logged-in student could subscribe to
 * /topic/account/{anyId} and watch a classmate's live fills, margin calls
 * and achievement unlocks — this is where that gap gets closed:
 *
 *  - CONNECT must carry a valid JWT as a STOMP header (the client sends it
 *    via STOMP connectHeaders, not an HTTP header) or the session is
 *    rejected outright.
 *  - SUBSCRIBE to /topic/account/{accountId} is only allowed when the
 *    connected user owns that account.
 *  - /topic/clock/** and /topic/prices/** carry no student-identifying
 *    data, so any authenticated session may subscribe.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final Pattern ACCOUNT_TOPIC = Pattern.compile("^/topic/account/(\\d+)$");

    private final JwtService jwt;
    private final AccountRepo accountRepo;

    public StompAuthChannelInterceptor(JwtService jwt, AccountRepo accountRepo) {
        this.jwt = jwt;
        this.accountRepo = accountRepo;
    }

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            authenticate(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            authorizeSubscription(accessor);
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new StompAuthException("Missing bearer token on STOMP CONNECT");
        }
        Claims claims;
        try {
            claims = jwt.parse(header.substring(7));
        } catch (Exception e) {
            throw new StompAuthException("Invalid or expired token");
        }
        // Deliberately outside the catch above: a failure here is a real bug (e.g. this
        // frame wasn't left mutable by the STOMP decoder), not an auth failure, and
        // shouldn't be mislabeled as one.
        accessor.setUser(new StompPrincipal(Long.parseLong(claims.getSubject())));
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        Principal principal = accessor.getUser();
        if (!(principal instanceof StompPrincipal(long userId))) {
            throw new StompAuthException("Not authenticated");
        }
        String destination = accessor.getDestination();
        Matcher m = destination == null ? null : ACCOUNT_TOPIC.matcher(destination);
        if (m == null || !m.matches()) return; // clock/prices topics: authentication alone is enough

        long accountId = Long.parseLong(m.group(1));
        Account account = accountRepo.findById(accountId).orElse(null);
        if (account == null || !account.userId.equals(userId)) {
            throw new StompAuthException("Not authorized to subscribe to this account's topic");
        }
    }
}
