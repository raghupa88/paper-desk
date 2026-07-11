package com.paperdesk.web;

import com.paperdesk.config.JwtService;
import com.paperdesk.domain.Cohort;
import com.paperdesk.domain.CohortMember;
import com.paperdesk.domain.Enums.Role;
import com.paperdesk.domain.User;
import com.paperdesk.repo.CohortMemberRepo;
import com.paperdesk.repo.CohortRepo;
import com.paperdesk.repo.ScenarioRepo;
import com.paperdesk.repo.UserRepo;
import com.paperdesk.sim.SessionService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/** HTTP-level test: the gradebook export needs real headers (content-type, Content-Disposition), not just a JSON body. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CohortCsvExportTest {

    @Autowired TestRestTemplate rest;
    @Autowired JwtService jwt;
    @Autowired UserRepo userRepo;
    @Autowired ScenarioRepo scenarioRepo;
    @Autowired CohortRepo cohortRepo;
    @Autowired CohortMemberRepo memberRepo;
    @Autowired SessionService sessions;

    long cohortId;
    String instructorToken;
    String outsiderToken;

    @BeforeAll
    void setup() {
        long scenarioId = scenarioRepo.findByName("Calm market").orElseThrow().id;

        User instructor = newUser("instructor@csv.io", "Prof Csv", Role.INSTRUCTOR);
        instructorToken = jwt.issue(instructor);
        User student = newUser("student@csv.io", "Stu Dent", Role.STUDENT);
        User outsider = newUser("outsider@csv.io", "Out Sider", Role.STUDENT);
        outsiderToken = jwt.issue(outsider);

        var session = sessions.createSession(scenarioId, null);
        Cohort cohort = new Cohort();
        cohort.name = "FIN 301!";
        cohort.instructorId = instructor.id;
        cohort.scenarioId = scenarioId;
        cohort.sessionId = session.id;
        cohort.startingBalance = 50000;
        cohort.joinCode = "ABC123";
        cohortRepo.save(cohort);
        cohortId = cohort.id;

        CohortMember member = new CohortMember();
        member.cohortId = cohort.id;
        member.userId = student.id;
        memberRepo.save(member);
        sessions.openAccount(student.id, session.id, cohort.startingBalance);
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
    void instructorCanDownloadTheLeaderboardAsCsv() {
        ResponseEntity<String> res = rest.exchange(
                "/api/cohorts/" + cohortId + "/leaderboard.csv", HttpMethod.GET, authed(instructorToken), String.class);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getHeaders().getContentType());
        assertTrue(res.getHeaders().getContentType().toString().startsWith("text/csv"));

        String disposition = res.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertNotNull(disposition);
        assertTrue(disposition.contains("attachment"), disposition);
        assertTrue(disposition.contains("FIN_301_-leaderboard.csv"), disposition);

        String body = res.getBody();
        assertNotNull(body);
        String[] lines = body.split("\n");
        assertEquals("Rank,Student,Equity,Return %,Max Drawdown %,XP,Level,Level Name", lines[0].trim());
        assertTrue(body.contains("Stu Dent"), body);
        assertTrue(body.contains("1,Stu Dent"), "sole student should be ranked #1: " + body);
    }

    @Test
    void nonMemberCannotDownloadTheLeaderboard() {
        ResponseEntity<String> res = rest.exchange(
                "/api/cohorts/" + cohortId + "/leaderboard.csv", HttpMethod.GET, authed(outsiderToken), String.class);
        assertEquals(HttpStatus.FORBIDDEN, res.getStatusCode());
    }

    @Test
    void jsonLeaderboardStillMatchesTheCsvRanking() {
        ResponseEntity<Object[]> json = rest.exchange(
                "/api/cohorts/" + cohortId + "/leaderboard", HttpMethod.GET, authed(instructorToken), Object[].class);
        assertEquals(HttpStatus.OK, json.getStatusCode());
        assertNotNull(json.getBody());
        assertEquals(1, json.getBody().length, "one enrolled student");
    }
}
