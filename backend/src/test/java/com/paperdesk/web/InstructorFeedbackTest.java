package com.paperdesk.web;

import com.paperdesk.config.JwtService;
import com.paperdesk.domain.Account;
import com.paperdesk.domain.Cohort;
import com.paperdesk.domain.CohortMember;
import com.paperdesk.domain.Enums.OrderSide;
import com.paperdesk.domain.Enums.OrderType;
import com.paperdesk.domain.Enums.Role;
import com.paperdesk.domain.Instrument;
import com.paperdesk.domain.TradeOrder;
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

/** HTTP-level: the read-only instructor endpoints and comment authorization are the point under test here. */
@SuppressWarnings("unchecked")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InstructorFeedbackTest {

    @Autowired TestRestTemplate rest;
    @Autowired JwtService jwt;
    @Autowired UserRepo userRepo;
    @Autowired ScenarioRepo scenarioRepo;
    @Autowired CohortRepo cohortRepo;
    @Autowired CohortMemberRepo memberRepo;
    @Autowired InstrumentRepo instrumentRepo;
    @Autowired SessionService sessions;
    @Autowired OrderService orders;

    long studentAccountId;
    long orderId;
    String instructorToken;
    String studentToken;
    String outsiderToken;

    @BeforeAll
    void setup() {
        long scenarioId = scenarioRepo.findByName("Calm market").orElseThrow().id;

        User instructor = newUser("instructor@grade.io", "Prof Grade", Role.INSTRUCTOR);
        instructorToken = jwt.issue(instructor);
        User student = newUser("student@grade.io", "Stu Dent", Role.STUDENT);
        studentToken = jwt.issue(student);
        User outsider = newUser("outsider@grade.io", "Out Sider", Role.STUDENT);
        outsiderToken = jwt.issue(outsider);

        var session = sessions.createSession(scenarioId, null);
        Cohort cohort = new Cohort();
        cohort.name = "FIN 401";
        cohort.instructorId = instructor.id;
        cohort.scenarioId = scenarioId;
        cohort.sessionId = session.id;
        cohort.startingBalance = 50000;
        cohort.joinCode = "GRADE1";
        cohortRepo.save(cohort);

        CohortMember member = new CohortMember();
        member.cohortId = cohort.id;
        member.userId = student.id;
        memberRepo.save(member);
        Account account = sessions.openAccount(student.id, session.id, cohort.startingBalance);
        studentAccountId = account.id;

        Instrument acme = instrumentRepo.findBySessionIdAndSymbol(session.id, "ACME").orElseThrow();
        TradeOrder order = orders.place(studentAccountId, acme.id, OrderSide.BUY, OrderType.MARKET, 10, null, null);
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

    private <T> HttpEntity<T> authedBody(String token, T body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    @Test
    void instructorCanViewStudentDetailButOutsiderCannot() {
        ResponseEntity<Map> ok = rest.exchange("/api/accounts/" + studentAccountId + "/detail",
                HttpMethod.GET, authed(instructorToken), Map.class);
        assertEquals(HttpStatus.OK, ok.getStatusCode());
        assertEquals("Stu Dent", ok.getBody().get("displayName"));
        assertNotNull(ok.getBody().get("portfolio"));
        assertNotNull(ok.getBody().get("scorecard"));
        assertTrue(((java.util.List<?>) ok.getBody().get("blotter")).size() >= 1);

        ResponseEntity<Map> forbidden = rest.exchange("/api/accounts/" + studentAccountId + "/detail",
                HttpMethod.GET, authed(outsiderToken), Map.class);
        assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatusCode());
    }

    @Test
    void instructorCanGradeAndTheStudentCanReadButNotWriteTheirOwnGrade() {
        Map<String, Object> body = Map.of(
                "riskManagementScore", 4, "disciplineScore", 5,
                "diversificationScore", 3, "overallScore", 4,
                "feedback", "Good use of stop levels this week.");

        ResponseEntity<Map> put = rest.exchange("/api/accounts/" + studentAccountId + "/grade",
                HttpMethod.PUT, authedBody(instructorToken, body), Map.class);
        assertEquals(HttpStatus.OK, put.getStatusCode());
        assertEquals(4, put.getBody().get("overallScore"));

        ResponseEntity<Map> studentRead = rest.exchange("/api/accounts/" + studentAccountId + "/grade",
                HttpMethod.GET, authed(studentToken), Map.class);
        assertEquals(HttpStatus.OK, studentRead.getStatusCode());
        assertEquals(true, studentRead.getBody().get("exists"));
        assertEquals("Good use of stop levels this week.", studentRead.getBody().get("feedback"));

        ResponseEntity<Map> studentWriteAttempt = rest.exchange("/api/accounts/" + studentAccountId + "/grade",
                HttpMethod.PUT, authedBody(studentToken, body), Map.class);
        assertEquals(HttpStatus.FORBIDDEN, studentWriteAttempt.getStatusCode());

        ResponseEntity<Map> outsiderRead = rest.exchange("/api/accounts/" + studentAccountId + "/grade",
                HttpMethod.GET, authed(outsiderToken), Map.class);
        assertEquals(HttpStatus.FORBIDDEN, outsiderRead.getStatusCode());
    }

    @Test
    void instructorCanCommentOnATradeAndTheStudentCanReadIt() {
        Map<String, String> body = Map.of("comment", "Nice entry, but watch your position sizing.");
        ResponseEntity<Map> posted = rest.exchange("/api/orders/" + orderId + "/comments",
                HttpMethod.POST, authedBody(instructorToken, body), Map.class);
        assertEquals(HttpStatus.OK, posted.getStatusCode());
        assertEquals("Prof Grade", posted.getBody().get("instructorName"));

        ResponseEntity<Object[]> studentRead = rest.exchange("/api/orders/" + orderId + "/comments",
                HttpMethod.GET, authed(studentToken), Object[].class);
        assertEquals(HttpStatus.OK, studentRead.getStatusCode());
        assertEquals(1, studentRead.getBody().length);

        ResponseEntity<Map> studentPostAttempt = rest.exchange("/api/orders/" + orderId + "/comments",
                HttpMethod.POST, authedBody(studentToken, body), Map.class);
        assertEquals(HttpStatus.FORBIDDEN, studentPostAttempt.getStatusCode());

        ResponseEntity<String> outsiderRead = rest.exchange("/api/orders/" + orderId + "/comments",
                HttpMethod.GET, authed(outsiderToken), String.class);
        assertEquals(HttpStatus.FORBIDDEN, outsiderRead.getStatusCode());
    }
}
