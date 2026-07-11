package com.paperdesk;

import com.paperdesk.domain.Account;
import com.paperdesk.domain.Enums.*;
import com.paperdesk.domain.Instrument;
import com.paperdesk.domain.User;
import com.paperdesk.gamification.Badge;
import com.paperdesk.gamification.GamificationService;
import com.paperdesk.gamification.Levels;
import com.paperdesk.gamification.Mission;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GamificationTest {

    @Autowired SimEngine engine;
    @Autowired SessionService sessions;
    @Autowired OrderService orders;
    @Autowired GamificationService gamification;
    @Autowired ScenarioRepo scenarioRepo;
    @Autowired AccountRepo accountRepo;
    @Autowired InstrumentRepo instrumentRepo;
    @Autowired AchievementRepo achievementRepo;
    @Autowired MissionCompletionRepo missionRepo;
    @Autowired UserRepo userRepo;

    long userId;
    long scenarioId;

    @BeforeAll
    void setup() {
        scenarioId = scenarioRepo.findByName("Calm market").orElseThrow().id;
        User u = new User();
        u.email = "gamer@test.io";
        u.passwordHash = "x";
        u.displayName = "Gamer";
        u.role = Role.STUDENT;
        userRepo.save(u);
        userId = u.id;
    }

    @Test
    void fillsAwardXpAndUnlockBadges() {
        var session = sessions.createSession(scenarioId, null);
        Account acct = sessions.openAccount(userId, session.id, 100000);

        Instrument acme = instrumentRepo.findBySessionIdAndSymbol(acct.sessionId, "ACME").orElseThrow();
        orders.place(acct.id, acme.id, OrderSide.BUY, OrderType.MARKET, 10, null, null);

        Account after = accountRepo.findById(acct.id).orElseThrow();
        List<String> codes = achievementRepo.findByAccountId(acct.id).stream().map(a -> a.code).toList();
        assertTrue(codes.contains(Badge.FIRST_TRADE.name()));
        assertTrue(codes.contains(Badge.STOCK_PICKER.name()));
        // A single trade also satisfies both FIRST_STEPS mission steps (a fill, and an open
        // position), so it completes in the same transaction as these two badges.
        assertTrue(missionRepo.existsByAccountIdAndCode(acct.id, Mission.FIRST_STEPS.name()));
        assertEquals(GamificationService.XP_PER_FILL + Badge.FIRST_TRADE.xp + Badge.STOCK_PICKER.xp + Mission.FIRST_STEPS.xp,
                after.xp, 1e-9, "fill xp + both badges + the First Steps mission");

        // second equity trade: only fill xp, badges are one-time
        orders.place(acct.id, acme.id, OrderSide.SELL, OrderType.MARKET, 5, null, null);
        Account after2 = accountRepo.findById(acct.id).orElseThrow();
        assertEquals(after.xp + GamificationService.XP_PER_FILL, after2.xp, 1e-9);
    }

    @Test
    void diversificationUnlocksAfterFourInstrumentTypes() {
        var session = sessions.createSession(scenarioId, null);
        Account acct = sessions.openAccount(userId, session.id, 100000);
        long sid = acct.sessionId;

        Instrument acme = instrumentRepo.findBySessionIdAndSymbol(sid, "ACME").orElseThrow();
        Instrument fut = instrumentRepo.findBySessionIdAndInstrumentTypeAndActiveTrue(sid, InstrumentType.FUTURE).get(0);
        Instrument opt = instrumentRepo.findBySessionIdAndInstrumentTypeAndActiveTrue(sid, InstrumentType.OPTION).get(0);
        Instrument fx = instrumentRepo.findBySessionIdAndSymbol(sid, "EURUSD").orElseThrow();

        orders.place(acct.id, acme.id, OrderSide.BUY, OrderType.MARKET, 1, null, null);
        orders.place(acct.id, fut.id, OrderSide.BUY, OrderType.MARKET, 1, null, null);
        orders.place(acct.id, opt.id, OrderSide.BUY, OrderType.MARKET, 1, null, null);
        assertFalse(achievementRepo.existsByAccountIdAndCode(acct.id, Badge.WELL_DIVERSIFIED.name()));
        orders.place(acct.id, fx.id, OrderSide.BUY, OrderType.MARKET, 100, null, null);
        assertTrue(achievementRepo.existsByAccountIdAndCode(acct.id, Badge.WELL_DIVERSIFIED.name()));
    }

    @Test
    void dayCloseAwardsProfitableDayWhenEquityRises() {
        var session = sessions.createSession(scenarioId, null);
        Account acct = sessions.openAccount(userId, session.id, 100000);
        // hold nothing; step days until a green day happens or give up — with no
        // positions equity is flat, so force it by direct service call instead
        Account managed = accountRepo.findById(acct.id).orElseThrow();
        gamification.onDayClosed(managed, engine.runtime(session.id).simDate(),
                managed.startingBalance + 500, engine.runtime(session.id));
        assertTrue(achievementRepo.existsByAccountIdAndCode(acct.id, Badge.PROFITABLE_DAY.name()));
    }

    @Test
    void levelThresholdsProgress() {
        assertEquals(1, Levels.forXp(0).number());
        assertEquals("Observer", Levels.forXp(50).name());
        assertEquals(2, Levels.forXp(100).number());
        assertEquals(8, Levels.forXp(99999).number());
        assertNull(Levels.forXp(99999).nextLevelXp(), "max level has no next threshold");
        assertEquals(250, Levels.forXp(120).nextLevelXp(), 1e-9);
    }
}
