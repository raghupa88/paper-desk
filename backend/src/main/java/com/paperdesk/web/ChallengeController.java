package com.paperdesk.web;

import com.paperdesk.config.SecurityConfig;
import com.paperdesk.domain.Account;
import com.paperdesk.domain.Challenge;
import com.paperdesk.domain.ChallengeEntry;
import com.paperdesk.domain.Cohort;
import com.paperdesk.domain.CohortMember;
import com.paperdesk.domain.User;
import com.paperdesk.repo.*;
import com.paperdesk.sim.SimEngine;
import com.paperdesk.trading.PortfolioService;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Timed challenges: instructor-initiated sprints scoped to a cohort, each
 * with its own equity-since-challenge-start leaderboard layered on top of
 * the always-on cohort leaderboard (CohortController). A recurring hook for
 * short competitive events (e.g. "one sim week, ranked") rather than the
 * single ongoing standings table.
 */
@RestController
@RequestMapping("/api/cohorts/{cohortId}/challenges")
public class ChallengeController {

    public record CreateChallengeRequest(String name, int durationSimDays) {}

    private final ChallengeRepo challengeRepo;
    private final ChallengeEntryRepo entryRepo;
    private final CohortRepo cohortRepo;
    private final CohortMemberRepo memberRepo;
    private final UserRepo userRepo;
    private final AccountRepo accountRepo;
    private final SimEngine engine;
    private final PortfolioService portfolio;

    public ChallengeController(ChallengeRepo challengeRepo, ChallengeEntryRepo entryRepo, CohortRepo cohortRepo,
                               CohortMemberRepo memberRepo, UserRepo userRepo, AccountRepo accountRepo,
                               SimEngine engine, PortfolioService portfolio) {
        this.challengeRepo = challengeRepo;
        this.entryRepo = entryRepo;
        this.cohortRepo = cohortRepo;
        this.memberRepo = memberRepo;
        this.userRepo = userRepo;
        this.accountRepo = accountRepo;
        this.engine = engine;
        this.portfolio = portfolio;
    }

    /** Enrolls every current cohort member with their equity right now as the challenge's baseline. */
    @PostMapping
    public Map<String, Object> create(@PathVariable long cohortId, @RequestBody CreateChallengeRequest req) {
        Cohort cohort = requireInstructor(cohortId);
        LocalDate startDate = engine.runtime(cohort.sessionId).simDate();

        Challenge challenge = new Challenge();
        challenge.cohortId = cohortId;
        challenge.name = req.name();
        challenge.durationSimDays = req.durationSimDays();
        challenge.startSimDate = startDate;
        challenge.endSimDate = startDate.plusDays(req.durationSimDays());
        challengeRepo.save(challenge);

        for (CohortMember member : memberRepo.findByCohortId(cohortId)) {
            accountRepo.findByUserIdAndSessionId(member.userId, cohort.sessionId).ifPresent(account -> {
                ChallengeEntry entry = new ChallengeEntry();
                entry.challengeId = challenge.id;
                entry.accountId = account.id;
                entry.startingEquity = portfolio.equity(account);
                entryRepo.save(entry);
            });
        }
        return challengeJson(challenge, cohort);
    }

    @GetMapping
    public List<Map<String, Object>> list(@PathVariable long cohortId) {
        Cohort cohort = requireMember(cohortId);
        return challengeRepo.findByCohortIdOrderByIdDesc(cohortId).stream()
                .map(c -> challengeJson(c, cohort)).toList();
    }

    private Map<String, Object> challengeJson(Challenge challenge, Cohort cohort) {
        LocalDate currentSimDate = engine.runtime(cohort.sessionId).simDate();
        boolean active = !currentSimDate.isAfter(challenge.endSimDate);

        record Scored(String displayName, double startingEquity, double currentEquity, double returnPct) {}
        List<Scored> scored = new ArrayList<>();
        for (ChallengeEntry entry : entryRepo.findByChallengeId(challenge.id)) {
            Optional<Account> account = accountRepo.findById(entry.accountId);
            if (account.isEmpty()) continue;
            User user = userRepo.findById(account.get().userId).orElseThrow();
            double currentEquity = portfolio.equity(account.get());
            double returnPct = entry.startingEquity == 0 ? 0
                    : (currentEquity / entry.startingEquity - 1) * 100;
            scored.add(new Scored(user.displayName, entry.startingEquity, currentEquity, returnPct));
        }
        scored.sort((a, b) -> Double.compare(b.returnPct(), a.returnPct()));

        List<Map<String, Object>> leaderboard = new ArrayList<>();
        for (int i = 0; i < scored.size(); i++) {
            Scored s = scored.get(i);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("rank", i + 1);
            row.put("displayName", s.displayName());
            row.put("startingEquity", s.startingEquity());
            row.put("currentEquity", s.currentEquity());
            row.put("returnPct", s.returnPct());
            leaderboard.add(row);
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("challengeId", challenge.id);
        m.put("cohortId", challenge.cohortId);
        m.put("name", challenge.name);
        m.put("durationSimDays", challenge.durationSimDays);
        m.put("startSimDate", challenge.startSimDate.toString());
        m.put("endSimDate", challenge.endSimDate.toString());
        m.put("active", active);
        m.put("leaderboard", leaderboard);
        return m;
    }

    private Cohort requireInstructor(long cohortId) {
        Cohort cohort = cohortRepo.findById(cohortId).orElseThrow();
        if (!cohort.instructorId.equals(SecurityConfig.currentUserId())) {
            throw new SecurityException("Instructor of this cohort required");
        }
        return cohort;
    }

    private Cohort requireMember(long cohortId) {
        long userId = SecurityConfig.currentUserId();
        Cohort cohort = cohortRepo.findById(cohortId).orElseThrow();
        boolean allowed = cohort.instructorId.equals(userId)
                || memberRepo.findByCohortIdAndUserId(cohortId, userId).isPresent();
        if (!allowed) throw new SecurityException("Not a member of this cohort");
        return cohort;
    }
}
