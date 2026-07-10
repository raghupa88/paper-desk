package com.paperdesk.web;

import com.paperdesk.config.SecurityConfig;
import com.paperdesk.domain.Account;
import com.paperdesk.domain.Cohort;
import com.paperdesk.domain.CohortMember;
import com.paperdesk.domain.Enums.Role;
import com.paperdesk.domain.EquitySnapshot;
import com.paperdesk.domain.ScenarioSession;
import com.paperdesk.domain.User;
import com.paperdesk.repo.*;
import com.paperdesk.sim.SessionService;
import com.paperdesk.trading.PortfolioService;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.util.*;

@RestController
@RequestMapping("/api/cohorts")
public class CohortController {

    public record CreateCohortRequest(String name, long scenarioId, Double startingBalance) {}
    public record JoinCohortRequest(String joinCode) {}

    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final SecureRandom random = new SecureRandom();

    private final CohortRepo cohortRepo;
    private final CohortMemberRepo memberRepo;
    private final UserRepo userRepo;
    private final AccountRepo accountRepo;
    private final EquitySnapshotRepo snapshotRepo;
    private final ScenarioRepo scenarioRepo;
    private final ScenarioSessionRepo sessionRepo;
    private final SessionService sessions;
    private final PortfolioService portfolio;

    public CohortController(CohortRepo cohortRepo, CohortMemberRepo memberRepo, UserRepo userRepo,
                            AccountRepo accountRepo, EquitySnapshotRepo snapshotRepo, ScenarioRepo scenarioRepo,
                            ScenarioSessionRepo sessionRepo, SessionService sessions, PortfolioService portfolio) {
        this.cohortRepo = cohortRepo;
        this.memberRepo = memberRepo;
        this.userRepo = userRepo;
        this.accountRepo = accountRepo;
        this.snapshotRepo = snapshotRepo;
        this.scenarioRepo = scenarioRepo;
        this.sessionRepo = sessionRepo;
        this.sessions = sessions;
        this.portfolio = portfolio;
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody CreateCohortRequest req) {
        User user = requireInstructor();
        ScenarioSession session = sessions.createSession(req.scenarioId(), null);
        Cohort cohort = new Cohort();
        cohort.name = req.name();
        cohort.instructorId = user.id;
        cohort.scenarioId = req.scenarioId();
        cohort.sessionId = session.id;
        cohort.startingBalance = req.startingBalance() == null ? 100000 : req.startingBalance();
        cohort.joinCode = randomCode();
        cohortRepo.save(cohort);
        session.cohortId = cohort.id;
        sessionRepo.save(session);
        return cohortJson(cohort);
    }

    @PostMapping("/join")
    public Map<String, Object> join(@RequestBody JoinCohortRequest req) {
        long userId = SecurityConfig.currentUserId();
        Cohort cohort = cohortRepo.findByJoinCode(req.joinCode() == null ? "" : req.joinCode().trim().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Unknown join code"));
        memberRepo.findByCohortIdAndUserId(cohort.id, userId).orElseGet(() -> {
            CohortMember m = new CohortMember();
            m.cohortId = cohort.id;
            m.userId = userId;
            return memberRepo.save(m);
        });
        Account account = sessions.openAccount(userId, cohort.sessionId, cohort.startingBalance);
        Map<String, Object> out = cohortJson(cohort);
        out.put("accountId", account.id);
        return out;
    }

    /** Cohorts I teach (instructor) or belong to (student). */
    @GetMapping
    public List<Map<String, Object>> mine() {
        long userId = SecurityConfig.currentUserId();
        Map<Long, Cohort> found = new LinkedHashMap<>();
        cohortRepo.findByInstructorId(userId).forEach(c -> found.put(c.id, c));
        memberRepo.findByUserId(userId).forEach(m ->
                cohortRepo.findById(m.cohortId).ifPresent(c -> found.put(c.id, c)));
        return found.values().stream().map(this::cohortJson).toList();
    }

    @GetMapping("/{cohortId}/leaderboard")
    public List<Map<String, Object>> leaderboard(@PathVariable long cohortId) {
        long userId = SecurityConfig.currentUserId();
        Cohort cohort = cohortRepo.findById(cohortId).orElseThrow();
        boolean allowed = cohort.instructorId.equals(userId)
                || memberRepo.findByCohortIdAndUserId(cohortId, userId).isPresent();
        if (!allowed) throw new SecurityException("Not a member of this cohort");

        List<Map<String, Object>> rows = new ArrayList<>();
        for (CohortMember member : memberRepo.findByCohortId(cohortId)) {
            User user = userRepo.findById(member.userId).orElseThrow();
            Optional<Account> account = accountRepo.findByUserIdAndSessionId(member.userId, cohort.sessionId);
            if (account.isEmpty()) continue;
            double equity = portfolio.equity(account.get());
            double ret = (equity / account.get().startingBalance - 1) * 100;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("displayName", user.displayName);
            m.put("equity", equity);
            m.put("returnPct", ret);
            m.put("maxDrawdownPct", maxDrawdownPct(account.get(), equity));
            rows.add(m);
        }
        rows.sort((a, b) -> Double.compare((Double) b.get("equity"), (Double) a.get("equity")));
        for (int i = 0; i < rows.size(); i++) rows.get(i).put("rank", i + 1);
        return rows;
    }

    private double maxDrawdownPct(Account account, double currentEquity) {
        double peak = account.startingBalance;
        double maxDd = 0;
        List<EquitySnapshot> snaps = snapshotRepo.findByAccountIdOrderBySimDate(account.id);
        List<Double> series = new ArrayList<>(snaps.stream().map(s -> s.equity).toList());
        series.add(currentEquity);
        for (double eq : series) {
            peak = Math.max(peak, eq);
            if (peak > 0) maxDd = Math.max(maxDd, (peak - eq) / peak * 100);
        }
        return maxDd;
    }

    private User requireInstructor() {
        User user = userRepo.findById(SecurityConfig.currentUserId()).orElseThrow();
        if (user.role != Role.INSTRUCTOR) throw new SecurityException("Instructor role required");
        return user;
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) sb.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
        return sb.toString();
    }

    private Map<String, Object> cohortJson(Cohort c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cohortId", c.id);
        m.put("name", c.name);
        m.put("scenarioId", c.scenarioId);
        m.put("scenarioName", scenarioRepo.findById(c.scenarioId).map(s -> s.name).orElse(""));
        m.put("sessionId", c.sessionId);
        m.put("startingBalance", c.startingBalance);
        m.put("joinCode", c.joinCode);
        m.put("instructorId", c.instructorId);
        return m;
    }
}
