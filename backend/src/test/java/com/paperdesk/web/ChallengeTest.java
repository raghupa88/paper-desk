package com.paperdesk.web;

import com.paperdesk.config.JwtService;
import com.paperdesk.domain.Account;
import com.paperdesk.domain.Cohort;
import com.paperdesk.domain.CohortMember;
import com.paperdesk.domain.Enums.OrderSide;
import com.paperdesk.domain.Enums.OrderType;
import com.paperdesk.domain.Enums.Role;
import com.paperdesk.domain.Instrument;
import com.paperdesk.domain.User;
import com.paperdesk.repo.CohortMemberRepo;
import com.paperdesk.repo.CohortRepo;
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

@SuppressWarnings("unchecked")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChallengeTest {

    @Autowired TestRestTemplate rest;
    @Autowired JwtService jwt;
    @Autowired UserRepo userRepo;
    @Autowired ScenarioRepo scenarioRepo;
    @Autowired CohortRepo cohortRepo;
    @Autowired CohortMemberRepo memberRepo;
    @Autowired InstrumentRepo instrumentRepo;
    @Autowired SessionService sessions;
    @Autowired OrderService orders;

    long cohortId;
    long studentAccountId;
    String instructorToken;
    String studentToken;
    String outsiderToken;

    @BeforeAll
    void setup() {
        long scenarioId = scenarioRepo.findByName("Calm market").orElseThrow().id;

        User instructor = newUser("instructor@challenge.io", "Prof Challenge", Role.INSTRUCTOR);
        instructorToken = jwt.issue(instructor);
        User student = newUser("student@challenge.io", "Stu Dent", Role.STUDENT);
        studentToken = jwt.issue(student);
        User outsider = newUser("outsider@challenge.io", "Out Sider", Role.STUDENT);
        outsiderToken = jwt.issue(outsider);

        var session = sessions.createSession(scenarioId, null);
        Cohort cohort = new Cohort();
        cohort.name = "Sprint 401";
        cohort.instructorId = instructor.id;
        cohort.scenarioId = scenarioId;
        cohort.sessionId = session.id;
        cohort.startingBalance = 50000;
        cohort.joinCode = "SPRINT1";
        cohortRepo.save(cohort);
        cohortId = cohort.id;

        CohortMember member = new CohortMember();
        member.cohortId = cohort.id;
        member.userId = student.id;
        memberRepo.save(member);
        Account account = sessions.openAccount(student.id, session.id, cohort.startingBalance);
        studentAccountId = account.id;
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

    private <T> HttpEntity<T> authedBody(String token, T body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    @Test
    void studentAndOutsiderCannotCreateAChallenge() {
        Map<String, Object> body = Map.of("name", "Week 1 Sprint", "durationSimDays", 7);
        ResponseEntity<Map> studentAttempt = rest.exchange("/api/cohorts/" + cohortId + "/challenges",
                HttpMethod.POST, authedBody(studentToken, body), Map.class);
        assertEquals(HttpStatus.FORBIDDEN, studentAttempt.getStatusCode());

        ResponseEntity<Map> outsiderAttempt = rest.exchange("/api/cohorts/" + cohortId + "/challenges",
                HttpMethod.POST, authedBody(outsiderToken, body), Map.class);
        assertEquals(HttpStatus.FORBIDDEN, outsiderAttempt.getStatusCode());
    }

    @Test
    void outsiderCannotListChallenges() {
        ResponseEntity<String> res = rest.exchange("/api/cohorts/" + cohortId + "/challenges",
                HttpMethod.GET, authed(outsiderToken), String.class);
        assertEquals(HttpStatus.FORBIDDEN, res.getStatusCode());
    }

    @Test
    void instructorCreatesAChallengeAndTheMemberIsAutoEnrolledAtParEquity() {
        Map<String, Object> body = Map.of("name", "Week 1 Sprint", "durationSimDays", 7);
        ResponseEntity<Map> created = rest.exchange("/api/cohorts/" + cohortId + "/challenges",
                HttpMethod.POST, authedBody(instructorToken, body), Map.class);
        assertEquals(HttpStatus.OK, created.getStatusCode());
        assertEquals("Week 1 Sprint", created.getBody().get("name"));
        assertEquals(true, created.getBody().get("active"));

        var leaderboard = (java.util.List<Map<String, Object>>) created.getBody().get("leaderboard");
        assertEquals(1, leaderboard.size(), "the sole cohort member should be auto-enrolled");
        assertEquals("Stu Dent", leaderboard.get(0).get("displayName"));
        assertEquals(1, leaderboard.get(0).get("rank"));
        assertEquals(0.0, (double) leaderboard.get(0).get("returnPct"), 1e-9, "no equity change yet");

        // Now the student trades; the challenge-scoped leaderboard should move
        // even though no sim time has passed (spread-driven, deterministic --
        // same mechanic verified in ScorecardTest).
        Instrument acme = instrumentRepo.findBySessionIdAndSymbol(
                cohortRepo.findById(cohortId).orElseThrow().sessionId, "ACME").orElseThrow();
        orders.place(studentAccountId, acme.id, OrderSide.BUY, OrderType.MARKET, 10, null, null);

        // The student themself can also read the challenge (cohort membership, not just instructing).
        ResponseEntity<java.util.List> after = rest.exchange("/api/cohorts/" + cohortId + "/challenges",
                HttpMethod.GET, authed(studentToken), java.util.List.class);
        assertEquals(HttpStatus.OK, after.getStatusCode());
        Map<String, Object> challenge = (Map<String, Object>) after.getBody().get(0);
        var updatedBoard = (java.util.List<Map<String, Object>>) challenge.get("leaderboard");
        double returnPct = (double) updatedBoard.get(0).get("returnPct");
        assertTrue(returnPct < 0, "buying at the ask with no tick in between realizes a spread-driven dip: " + returnPct);
    }
}
