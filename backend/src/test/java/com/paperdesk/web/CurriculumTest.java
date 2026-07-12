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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("unchecked")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CurriculumTest {

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

        User instructor = newUser("instructor@curriculum.io", "Prof Syllabus", Role.INSTRUCTOR);
        instructorToken = jwt.issue(instructor);
        User student = newUser("student@curriculum.io", "Stu Dent", Role.STUDENT);
        studentToken = jwt.issue(student);
        User outsider = newUser("outsider@curriculum.io", "Out Sider", Role.STUDENT);
        outsiderToken = jwt.issue(outsider);

        var session = sessions.createSession(scenarioId, null);
        Cohort cohort = new Cohort();
        cohort.name = "Options 101";
        cohort.instructorId = instructor.id;
        cohort.scenarioId = scenarioId;
        cohort.sessionId = session.id;
        cohort.startingBalance = 50000;
        cohort.joinCode = "SYLLABUS1";
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
    void studentAndOutsiderCannotCreateACurriculum() {
        Map<String, Object> body = Map.of("name", "Options 101", "description", "Learn the basics",
                "missionCodes", List.of("FIRST_STEPS"));
        assertEquals(HttpStatus.FORBIDDEN, rest.exchange("/api/cohorts/" + cohortId + "/curricula",
                HttpMethod.POST, authedBody(studentToken, body), Map.class).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, rest.exchange("/api/cohorts/" + cohortId + "/curricula",
                HttpMethod.POST, authedBody(outsiderToken, body), Map.class).getStatusCode());
    }

    @Test
    void outsiderCannotListCurricula() {
        ResponseEntity<String> res = rest.exchange("/api/cohorts/" + cohortId + "/curricula",
                HttpMethod.GET, authed(outsiderToken), String.class);
        assertEquals(HttpStatus.FORBIDDEN, res.getStatusCode());
    }

    @Test
    void invalidMissionCodeIsRejected() {
        Map<String, Object> body = Map.of("name", "Bad", "description", "", "missionCodes", List.of("NOT_A_MISSION"));
        ResponseEntity<Map> res = rest.exchange("/api/cohorts/" + cohortId + "/curricula",
                HttpMethod.POST, authedBody(instructorToken, body), Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
    }

    @Test
    void stepsUnlockInOrderAsTheStudentCompletesMissions() {
        Map<String, Object> body = Map.of("name", "Options 101", "description", "Learn the basics",
                "missionCodes", List.of("FIRST_STEPS", "COVERED_CALL"));
        ResponseEntity<Map> created = rest.exchange("/api/cohorts/" + cohortId + "/curricula",
                HttpMethod.POST, authedBody(instructorToken, body), Map.class);
        assertEquals(HttpStatus.OK, created.getStatusCode());

        List<Map<String, Object>> steps = (List<Map<String, Object>>) created.getBody().get("steps");
        assertEquals(2, steps.size());
        assertEquals("FIRST_STEPS", steps.get(0).get("missionCode"));
        assertEquals(true, steps.get(0).get("unlocked"));
        assertEquals(false, steps.get(0).get("complete"));
        assertEquals("COVERED_CALL", steps.get(1).get("missionCode"));
        assertEquals(false, steps.get(1).get("unlocked"), "later steps start locked");
        assertTrue((int) steps.get(0).get("xp") > 0);

        // The student completes FIRST_STEPS (a single filled buy satisfies both its steps).
        Instrument acme = instrumentRepo.findBySessionIdAndSymbol(
                cohortRepo.findById(cohortId).orElseThrow().sessionId, "ACME").orElseThrow();
        orders.place(studentAccountId, acme.id, OrderSide.BUY, OrderType.MARKET, 1, null, null);

        ResponseEntity<List> after = rest.exchange("/api/cohorts/" + cohortId + "/curricula",
                HttpMethod.GET, authed(studentToken), List.class);
        assertEquals(HttpStatus.OK, after.getStatusCode());
        Map<String, Object> curriculum = (Map<String, Object>) after.getBody().get(0);
        List<Map<String, Object>> updatedSteps = (List<Map<String, Object>>) curriculum.get("steps");
        assertEquals(true, updatedSteps.get(0).get("complete"));
        assertEquals(true, updatedSteps.get(1).get("unlocked"), "completing step 1 unlocks step 2");
        assertEquals(false, updatedSteps.get(1).get("complete"), "step 2 itself isn't done yet");
    }
}
