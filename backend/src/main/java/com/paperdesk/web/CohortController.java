package com.paperdesk.web;

import com.paperdesk.config.SecurityConfig;
import com.paperdesk.domain.Account;
import com.paperdesk.domain.Cohort;
import com.paperdesk.domain.CohortMember;
import com.paperdesk.domain.Enums.Role;
import com.paperdesk.domain.EquitySnapshot;
import com.paperdesk.domain.ScenarioSession;
import com.paperdesk.domain.User;
import com.paperdesk.gamification.Levels;
import com.paperdesk.repo.*;
import com.paperdesk.sim.SessionService;
import com.paperdesk.trading.PortfolioService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.util.*;

@RestController
@RequestMapping("/api/cohorts")
public class CohortController {

    public record CreateCohortRequest(String name, long scenarioId, Double startingBalance) {}
    public record JoinCohortRequest(String joinCode) {}

    /** One ranked leaderboard row — the shared shape behind both the JSON and CSV endpoints. */
    public record LeaderboardEntry(int rank, String displayName, double equity, double returnPct,
                                   double maxDrawdownPct, double xp, int level, String levelName) {}

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
        return computeLeaderboard(cohortId).stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("rank", e.rank());
            m.put("displayName", e.displayName());
            m.put("equity", e.equity());
            m.put("returnPct", e.returnPct());
            m.put("maxDrawdownPct", e.maxDrawdownPct());
            m.put("xp", e.xp());
            m.put("level", e.level());
            m.put("levelName", e.levelName());
            return m;
        }).toList();
    }

    /**
     * Gradebook export: the same ranking as the live leaderboard, as a CSV
     * download. Fetched via a bearer-token'd request from the frontend
     * (DataService.downloadLeaderboardCsv) rather than a plain link, since
     * a browser navigation can't carry the Authorization header this
     * endpoint needs — same authorization rule as the JSON leaderboard.
     */
    @GetMapping(value = "/{cohortId}/leaderboard.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> leaderboardCsv(@PathVariable long cohortId) {
        Cohort cohort = cohortRepo.findById(cohortId).orElseThrow();
        List<LeaderboardEntry> rows = computeLeaderboard(cohortId);

        StringBuilder csv = new StringBuilder("Rank,Student,Equity,Return %,Max Drawdown %,XP,Level,Level Name\n");
        for (LeaderboardEntry e : rows) {
            csv.append(e.rank()).append(',')
               .append(csvField(e.displayName())).append(',')
               .append(String.format(Locale.ROOT, "%.2f", e.equity())).append(',')
               .append(String.format(Locale.ROOT, "%.2f", e.returnPct())).append(',')
               .append(String.format(Locale.ROOT, "%.2f", e.maxDrawdownPct())).append(',')
               .append(String.format(Locale.ROOT, "%.0f", e.xp())).append(',')
               .append(e.level()).append(',')
               .append(csvField(e.levelName())).append('\n');
        }

        String filename = cohort.name.replaceAll("[^a-zA-Z0-9-]+", "_") + "-leaderboard.csv";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(csv.toString());
    }

    private List<LeaderboardEntry> computeLeaderboard(long cohortId) {
        long userId = SecurityConfig.currentUserId();
        Cohort cohort = cohortRepo.findById(cohortId).orElseThrow();
        boolean allowed = cohort.instructorId.equals(userId)
                || memberRepo.findByCohortIdAndUserId(cohortId, userId).isPresent();
        if (!allowed) throw new SecurityException("Not a member of this cohort");

        record Scored(String displayName, double equity, double returnPct, double maxDrawdownPct,
                      double xp, int level, String levelName) {}
        List<Scored> scored = new ArrayList<>();
        for (CohortMember member : memberRepo.findByCohortId(cohortId)) {
            User user = userRepo.findById(member.userId).orElseThrow();
            Optional<Account> account = accountRepo.findByUserIdAndSessionId(member.userId, cohort.sessionId);
            if (account.isEmpty()) continue;
            double equity = portfolio.equity(account.get());
            double ret = (equity / account.get().startingBalance - 1) * 100;
            Levels.Level level = Levels.forXp(account.get().xp);
            scored.add(new Scored(user.displayName, equity, ret, maxDrawdownPct(account.get(), equity),
                    account.get().xp, level.number(), level.name()));
        }
        scored.sort((a, b) -> Double.compare(b.equity(), a.equity()));

        List<LeaderboardEntry> out = new ArrayList<>();
        for (int i = 0; i < scored.size(); i++) {
            Scored s = scored.get(i);
            out.add(new LeaderboardEntry(i + 1, s.displayName(), s.equity(), s.returnPct(),
                    s.maxDrawdownPct(), s.xp(), s.level(), s.levelName()));
        }
        return out;
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

    /** Quotes a CSV field only when it contains a comma, quote or newline, per RFC 4180. */
    private String csvField(String s) {
        if (s == null) return "";
        boolean needsQuote = s.contains(",") || s.contains("\"") || s.contains("\n");
        String escaped = s.replace("\"", "\"\"");
        return needsQuote ? "\"" + escaped + "\"" : escaped;
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
