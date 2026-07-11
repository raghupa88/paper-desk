package com.paperdesk.config;

import com.paperdesk.domain.Account;
import com.paperdesk.domain.Enums.Role;
import com.paperdesk.domain.User;
import com.paperdesk.repo.AccountRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.messaging.support.MessageBuilder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Exercises exactly the logic that stands between "any logged-in student"
 * and "another student's live account feed" — see StompAuthChannelInterceptor
 * for the full threat description. WebSocketAuthWiringTest in this package
 * separately proves this class is actually registered on the real channel.
 */
class StompAuthChannelInterceptorTest {

    private final JwtService jwt = new JwtService("test-only-secret-at-least-32-bytes-long-for-hmac", 1);
    private AccountRepo accountRepo;
    private StompAuthChannelInterceptor interceptor;
    private final ExecutorSubscribableChannel channel = new ExecutorSubscribableChannel();

    @BeforeEach
    void setup() {
        accountRepo = mock(AccountRepo.class);
        interceptor = new StompAuthChannelInterceptor(jwt, accountRepo);
    }

    private String tokenFor(long userId) {
        User u = new User();
        u.id = userId;
        u.email = "user" + userId + "@test.io";
        u.displayName = "User " + userId;
        u.role = Role.STUDENT;
        return jwt.issue(u);
    }

    private Message<byte[]> connectFrame(String authorizationHeader) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authorizationHeader != null) accessor.addNativeHeader("Authorization", authorizationHeader);
        // Real STOMP CONNECT frames arrive from Spring's decoder already left mutable —
        // that's what lets preSend() call accessor.setUser() and have it stick. Match that
        // here so this test reflects the real message shape the interceptor runs against.
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> subscribeFrame(String destination, StompPrincipal principal) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        if (principal != null) accessor.setUser(principal);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void connectWithoutAuthorizationHeaderIsRejected() {
        assertThrows(StompAuthException.class, () -> interceptor.preSend(connectFrame(null), channel));
    }

    @Test
    void connectWithMalformedHeaderIsRejected() {
        assertThrows(StompAuthException.class, () -> interceptor.preSend(connectFrame("not-a-bearer-value"), channel));
    }

    @Test
    void connectWithInvalidTokenIsRejected() {
        assertThrows(StompAuthException.class, () -> interceptor.preSend(connectFrame("Bearer garbage.not.a.jwt"), channel));
    }

    @Test
    void connectWithValidTokenAttachesThePrincipal() {
        Message<?> result = interceptor.preSend(connectFrame("Bearer " + tokenFor(42)), channel);
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(result);
        assertInstanceOf(StompPrincipal.class, accessor.getUser());
        assertEquals(42L, ((StompPrincipal) accessor.getUser()).userId());
    }

    @Test
    void subscribingWithoutAPriorConnectIsRejected() {
        Message<byte[]> message = subscribeFrame("/topic/account/7", null);
        assertThrows(StompAuthException.class, () -> interceptor.preSend(message, channel));
    }

    @Test
    void subscribingToOwnAccountTopicSucceeds() {
        Account account = new Account();
        account.id = 7L;
        account.userId = 42L;
        when(accountRepo.findById(7L)).thenReturn(Optional.of(account));

        Message<byte[]> message = subscribeFrame("/topic/account/7", new StompPrincipal(42L));
        assertDoesNotThrow(() -> interceptor.preSend(message, channel));
    }

    @Test
    void subscribingToAnotherStudentsAccountTopicIsRejected() {
        Account account = new Account();
        account.id = 7L;
        account.userId = 99L; // belongs to someone else
        when(accountRepo.findById(7L)).thenReturn(Optional.of(account));

        Message<byte[]> message = subscribeFrame("/topic/account/7", new StompPrincipal(42L));
        assertThrows(StompAuthException.class, () -> interceptor.preSend(message, channel));
    }

    @Test
    void subscribingToAnUnknownAccountTopicIsRejected() {
        when(accountRepo.findById(999L)).thenReturn(Optional.empty());
        Message<byte[]> message = subscribeFrame("/topic/account/999", new StompPrincipal(42L));
        assertThrows(StompAuthException.class, () -> interceptor.preSend(message, channel));
    }

    @Test
    void clockAndPricesTopicsOnlyRequireAuthentication_noOwnershipLookup() {
        Message<byte[]> clockMsg = subscribeFrame("/topic/clock/3", new StompPrincipal(42L));
        Message<byte[]> pricesMsg = subscribeFrame("/topic/prices/3", new StompPrincipal(42L));
        assertDoesNotThrow(() -> interceptor.preSend(clockMsg, channel));
        assertDoesNotThrow(() -> interceptor.preSend(pricesMsg, channel));
        verifyNoInteractions(accountRepo);
    }
}
