package com.paperdesk;

import com.paperdesk.domain.Account;
import com.paperdesk.domain.Enums.*;
import com.paperdesk.domain.Instrument;
import com.paperdesk.domain.User;
import com.paperdesk.gamification.Mission;
import com.paperdesk.gamification.MissionEvaluationService;
import com.paperdesk.repo.*;
import com.paperdesk.sim.SessionService;
import com.paperdesk.sim.SimEngine;
import com.paperdesk.trading.OrderService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MissionTest {

    @Autowired SimEngine engine;
    @Autowired SessionService sessions;
    @Autowired OrderService orders;
    @Autowired MissionEvaluationService missions;
    @Autowired ScenarioRepo scenarioRepo;
    @Autowired AccountRepo accountRepo;
    @Autowired InstrumentRepo instrumentRepo;
    @Autowired MissionCompletionRepo missionRepo;
    @Autowired UserRepo userRepo;

    long userId;
    long scenarioId;

    @BeforeAll
    void setup() {
        scenarioId = scenarioRepo.findByName("Calm market").orElseThrow().id;
        User u = new User();
        u.email = "missionrunner@test.io";
        u.passwordHash = "x";
        u.displayName = "Runner";
        u.role = Role.STUDENT;
        userRepo.save(u);
        userId = u.id;
    }

    @Test
    void coveredCallCompletesAfterSharesAndShortCall() {
        var session = sessions.createSession(scenarioId, null);
        Account acct = sessions.openAccount(userId, session.id, 100000);
        Instrument acme = instrumentRepo.findBySessionIdAndSymbol(acct.sessionId, "ACME").orElseThrow();

        orders.place(acct.id, acme.id, OrderSide.BUY, OrderType.MARKET, 100, null, null);
        assertFalse(missionRepo.existsByAccountIdAndCode(acct.id, Mission.COVERED_CALL.name()));

        List<Instrument> calls = instrumentRepo
                .findBySessionIdAndUnderlyingIdAndInstrumentTypeAndActiveTrue(acct.sessionId, acme.id, InstrumentType.OPTION)
                .stream().filter(o -> o.callPut == CallPut.CALL)
                .sorted(Comparator.comparing((Instrument o) -> o.expiryDate).thenComparing(o -> o.strike))
                .toList();
        Instrument call = calls.get(0);
        orders.place(acct.id, call.id, OrderSide.SELL, OrderType.MARKET, 1, null, null);

        assertTrue(missionRepo.existsByAccountIdAndCode(acct.id, Mission.COVERED_CALL.name()));
        Account after = accountRepo.findById(acct.id).orElseThrow();
        assertTrue(after.xp >= Mission.COVERED_CALL.xp, "mission xp awarded");
    }

    @Test
    void longStraddleRequiresMatchingCallAndPut() {
        var session = sessions.createSession(scenarioId, null);
        Account acct = sessions.openAccount(userId, session.id, 100000);
        Instrument acme = instrumentRepo.findBySessionIdAndSymbol(acct.sessionId, "ACME").orElseThrow();
        List<Instrument> chain = instrumentRepo
                .findBySessionIdAndUnderlyingIdAndInstrumentTypeAndActiveTrue(acct.sessionId, acme.id, InstrumentType.OPTION);
        Instrument call = chain.stream().filter(o -> o.callPut == CallPut.CALL)
                .min(Comparator.comparing((Instrument o) -> o.expiryDate).thenComparing(o -> o.strike)).orElseThrow();
        Instrument matchingPut = chain.stream()
                .filter(o -> o.callPut == CallPut.PUT && o.expiryDate.equals(call.expiryDate) && o.strike.equals(call.strike))
                .findFirst().orElseThrow();

        orders.place(acct.id, call.id, OrderSide.BUY, OrderType.MARKET, 1, null, null);
        assertFalse(missionRepo.existsByAccountIdAndCode(acct.id, Mission.LONG_STRADDLE.name()));
        orders.place(acct.id, matchingPut.id, OrderSide.BUY, OrderType.MARKET, 1, null, null);
        assertTrue(missionRepo.existsByAccountIdAndCode(acct.id, Mission.LONG_STRADDLE.name()));
    }

    @Test
    void firstStepsAndDuplicateFillsDoNotDoubleAwardMissionXp() {
        var session = sessions.createSession(scenarioId, null);
        Account acct = sessions.openAccount(userId, session.id, 100000);
        Instrument acme = instrumentRepo.findBySessionIdAndSymbol(acct.sessionId, "ACME").orElseThrow();

        orders.place(acct.id, acme.id, OrderSide.BUY, OrderType.MARKET, 1, null, null);
        assertTrue(missionRepo.existsByAccountIdAndCode(acct.id, Mission.FIRST_STEPS.name()));
        double xpAfterFirst = accountRepo.findById(acct.id).orElseThrow().xp;

        orders.place(acct.id, acme.id, OrderSide.BUY, OrderType.MARKET, 1, null, null);
        double xpAfterSecond = accountRepo.findById(acct.id).orElseThrow().xp;
        assertEquals(1, missionRepo.findByAccountId(acct.id).stream()
                .filter(m -> m.code.equals(Mission.FIRST_STEPS.name())).count());
        assertTrue(xpAfterSecond > xpAfterFirst, "per-fill xp still accrues, just not mission xp again");
    }

    @Test
    void evaluateReadOnlyDoesNotPersist() {
        var session = sessions.createSession(scenarioId, null);
        Account acct = sessions.openAccount(userId, session.id, 100000);
        var results = missions.evaluate(acct.id);
        assertEquals(Mission.values().length, results.size());
        assertTrue(results.stream().noneMatch(MissionEvaluationService.MissionResult::completed));
        assertTrue(missionRepo.findByAccountId(acct.id).isEmpty());
    }
}
