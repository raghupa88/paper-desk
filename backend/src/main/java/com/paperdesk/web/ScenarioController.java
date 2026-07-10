package com.paperdesk.web;

import com.paperdesk.config.SecurityConfig;
import com.paperdesk.domain.Account;
import com.paperdesk.domain.Cohort;
import com.paperdesk.domain.Scenario;
import com.paperdesk.domain.ScenarioSession;
import com.paperdesk.repo.AccountRepo;
import com.paperdesk.repo.CohortRepo;
import com.paperdesk.repo.ScenarioRepo;
import com.paperdesk.repo.ScenarioSessionRepo;
import com.paperdesk.sim.SessionRuntime;
import com.paperdesk.sim.SessionService;
import com.paperdesk.sim.SimEngine;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ScenarioController {

    public record ClockAction(String action, Double acceleration) {}

    private final ScenarioRepo scenarioRepo;
    private final ScenarioSessionRepo sessionRepo;
    private final AccountRepo accountRepo;
    private final CohortRepo cohortRepo;
    private final SessionService sessions;
    private final SimEngine engine;

    public ScenarioController(ScenarioRepo scenarioRepo, ScenarioSessionRepo sessionRepo, AccountRepo accountRepo,
                              CohortRepo cohortRepo, SessionService sessions, SimEngine engine) {
        this.scenarioRepo = scenarioRepo;
        this.sessionRepo = sessionRepo;
        this.accountRepo = accountRepo;
        this.cohortRepo = cohortRepo;
        this.sessions = sessions;
        this.engine = engine;
    }

    @GetMapping("/scenarios")
    public List<Map<String, Object>> scenarios() {
        return scenarioRepo.findAll().stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.id);
            m.put("name", s.name);
            m.put("description", s.description);
            m.put("acceleration", s.acceleration);
            return m;
        }).toList();
    }

    /** Join (find-or-create) the shared default session for a scenario and open an account. */
    @PostMapping("/scenarios/{scenarioId}/join")
    public Map<String, Object> join(@PathVariable long scenarioId) {
        Account account = sessions.join(SecurityConfig.currentUserId(), scenarioId);
        return accountJson(account);
    }

    /** All trading accounts of the current user across sessions. */
    @GetMapping("/accounts")
    public List<Map<String, Object>> myAccounts() {
        return accountRepo.findByUserId(SecurityConfig.currentUserId()).stream()
                .map(this::accountJson).toList();
    }

    @GetMapping("/sessions/{sessionId}/clock")
    public Map<String, Object> clock(@PathVariable long sessionId) {
        return clockJson(engine.runtime(sessionId));
    }

    @PostMapping("/sessions/{sessionId}/clock")
    public Map<String, Object> controlClock(@PathVariable long sessionId, @RequestBody ClockAction req) {
        ScenarioSession session = sessionRepo.findById(sessionId).orElseThrow();
        if (session.cohortId != null) {
            Cohort cohort = cohortRepo.findById(session.cohortId).orElseThrow();
            if (!cohort.instructorId.equals(SecurityConfig.currentUserId()))
                throw new SecurityException("Only the cohort's instructor can control this session's clock");
        }
        switch (req.action() == null ? "" : req.action()) {
            case "PAUSE" -> engine.setPaused(sessionId, true);
            case "RESUME" -> engine.setPaused(sessionId, false);
            case "STEP_DAY" -> engine.stepOneDay(sessionId);
            case "SET_ACCELERATION" -> {
                if (req.acceleration() == null) throw new IllegalArgumentException("acceleration required");
                engine.setAcceleration(sessionId, req.acceleration());
            }
            default -> throw new IllegalArgumentException("Unknown clock action: " + req.action());
        }
        return clockJson(engine.runtime(sessionId));
    }

    private Map<String, Object> clockJson(SessionRuntime rt) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sessionId", rt.sessionId);
        m.put("simTime", rt.simTime.toString());
        m.put("simDate", rt.simDate().toString());
        m.put("paused", rt.paused);
        m.put("acceleration", rt.acceleration);
        m.put("floatingRate", rt.floatingRate);
        return m;
    }

    private Map<String, Object> accountJson(Account account) {
        ScenarioSession session = sessionRepo.findById(account.sessionId).orElseThrow();
        Scenario scenario = scenarioRepo.findById(session.scenarioId).orElseThrow();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("accountId", account.id);
        m.put("sessionId", account.sessionId);
        m.put("scenarioId", scenario.id);
        m.put("scenarioName", scenario.name);
        m.put("cohortId", session.cohortId);
        m.put("cashBalance", account.cashBalance);
        m.put("startingBalance", account.startingBalance);
        return m;
    }
}
