package com.paperdesk.web;

import com.paperdesk.config.JwtService;
import com.paperdesk.domain.Enums.OrderSide;
import com.paperdesk.domain.Enums.OrderType;
import com.paperdesk.domain.Enums.Role;
import com.paperdesk.domain.Instrument;
import com.paperdesk.domain.TradeOrder;
import com.paperdesk.domain.User;
import com.paperdesk.repo.InstrumentRepo;
import com.paperdesk.repo.ScenarioRepo;
import com.paperdesk.repo.UserRepo;
import com.paperdesk.sim.SessionService;
import com.paperdesk.trading.OrderService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** HTTP-level: no ANTHROPIC_API_KEY is set under the test profile, so the point under test is the
 *  graceful not-configured degrade path plus ownership authorization -- not a real LLM call. */
@SuppressWarnings("unchecked")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CoachControllerTest {

    @Autowired TestRestTemplate rest;
    @Autowired JwtService jwt;
    @Autowired UserRepo userRepo;
    @Autowired ScenarioRepo scenarioRepo;
    @Autowired InstrumentRepo instrumentRepo;
    @Autowired SessionService sessions;
    @Autowired OrderService orders;

    long orderId;
    String ownerToken;
    String outsiderToken;

    @BeforeAll
    void setup() {
        long scenarioId = scenarioRepo.findByName("Calm market").orElseThrow().id;

        User owner = newUser("coachowner@paperdesk.io", "Coach Owner", Role.STUDENT);
        ownerToken = jwt.issue(owner);
        User outsider = newUser("coachoutsider@paperdesk.io", "Coach Outsider", Role.STUDENT);
        outsiderToken = jwt.issue(outsider);

        var session = sessions.createSession(scenarioId, null);
        var account = sessions.openAccount(owner.id, session.id, 100000);

        Instrument acme = instrumentRepo.findBySessionIdAndSymbol(session.id, "ACME").orElseThrow();
        TradeOrder order = orders.place(account.id, acme.id, OrderSide.BUY, OrderType.MARKET, 10, null, null);
        orderId = order.id;
    }

    private User newUser(String email, String name, Role role) {
        User u = new User();
        u.email = email;
        u.passwordHash = "x";
        u.displayName = name;
        u.role = role;
        userRepo.save(u);
        return u;
    }

    private HttpEntity<Void> authed(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }

    @Test
    void ownerGetsAGracefulNotConfiguredResponseAndOutsiderIsForbidden() {
        ResponseEntity<Map> ok = rest.exchange("/api/orders/" + orderId + "/explain",
                HttpMethod.POST, authed(ownerToken), Map.class);
        assertEquals(HttpStatus.OK, ok.getStatusCode());
        assertEquals(false, ok.getBody().get("configured"));
        assertNull(ok.getBody().get("explanation"));

        ResponseEntity<Map> forbidden = rest.exchange("/api/orders/" + orderId + "/explain",
                HttpMethod.POST, authed(outsiderToken), Map.class);
        assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatusCode());
    }
}
