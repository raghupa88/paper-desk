package com.paperdesk.web;

import com.paperdesk.domain.Account;
import com.paperdesk.domain.CohortGrade;
import com.paperdesk.domain.Fill;
import com.paperdesk.domain.Settlement;
import com.paperdesk.domain.TradeOrder;
import com.paperdesk.domain.User;
import com.paperdesk.repo.*;
import com.paperdesk.sim.MarketDataService;
import com.paperdesk.trading.PortfolioService;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Instructor-side, read-only access to a student's account (rubric-based
 * grading + a combined snapshot for review), layered entirely on top of
 * existing account-scoped services -- an instructor never places or cancels
 * orders on a student's behalf, only reads and grades.
 */
@RestController
@RequestMapping("/api/accounts/{accountId}")
public class InstructorFeedbackController {

    public record GradeRequest(int riskManagementScore, int disciplineScore,
                               int diversificationScore, int overallScore, String feedback) {}

    private final AccountGuard guard;
    private final AccountRepo accountRepo;
    private final UserRepo userRepo;
    private final CohortRepo cohortRepo;
    private final CohortGradeRepo gradeRepo;
    private final OrderRepo orderRepo;
    private final FillRepo fillRepo;
    private final SettlementRepo settlementRepo;
    private final MarketDataService market;
    private final PortfolioService portfolio;

    public InstructorFeedbackController(AccountGuard guard, AccountRepo accountRepo, UserRepo userRepo,
                                        CohortRepo cohortRepo, CohortGradeRepo gradeRepo, OrderRepo orderRepo,
                                        FillRepo fillRepo, SettlementRepo settlementRepo,
                                        MarketDataService market, PortfolioService portfolio) {
        this.guard = guard;
        this.accountRepo = accountRepo;
        this.userRepo = userRepo;
        this.cohortRepo = cohortRepo;
        this.gradeRepo = gradeRepo;
        this.orderRepo = orderRepo;
        this.fillRepo = fillRepo;
        this.settlementRepo = settlementRepo;
        this.market = market;
        this.portfolio = portfolio;
    }

    /** A student's portfolio + scorecard + blotter + settlements in one call, for instructor review. */
    @GetMapping("/detail")
    public Map<String, Object> detail(@PathVariable long accountId) {
        Account account = guard.requireInstructing(accountId);
        User student = userRepo.findById(account.userId).orElseThrow();

        List<TradeOrder> orders = orderRepo.findByAccountIdOrderByIdDesc(accountId);
        Map<Long, List<Fill>> fills = fillRepo.findByOrderIdIn(orders.stream().map(o -> o.id).toList())
                .stream().collect(Collectors.groupingBy(f -> f.orderId));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("accountId", accountId);
        out.put("displayName", student.displayName);
        out.put("portfolio", portfolio.portfolio(accountId));
        out.put("scorecard", portfolio.scorecard(accountId));
        out.put("blotter", orders.stream().map(o -> orderJson(o, fills.getOrDefault(o.id, List.of()))).toList());
        out.put("settlements", settlementRepo.findByAccountIdOrderByIdDesc(accountId).stream()
                .map(this::settlementJson).toList());
        return out;
    }

    /** {exists:false} for an account with no cohort or no grade yet -- not an error, the common case
     *  for a student who isn't (or isn't yet) in an instructor's cohort. */
    @GetMapping("/grade")
    public Map<String, Object> getGrade(@PathVariable long accountId) {
        Account account = guard.ownedOrInstructing(accountId);
        return cohortRepo.findBySessionId(account.sessionId)
                .flatMap(cohort -> gradeRepo.findByCohortIdAndAccountId(cohort.id, accountId))
                .map(this::gradeJson)
                .orElseGet(() -> Map.of("exists", false));
    }

    @PutMapping("/grade")
    public Map<String, Object> putGrade(@PathVariable long accountId, @RequestBody GradeRequest req) {
        Account account = guard.requireInstructing(accountId);
        var cohort = cohortRepo.findBySessionId(account.sessionId).orElseThrow();
        CohortGrade grade = gradeRepo.findByCohortIdAndAccountId(cohort.id, accountId).orElseGet(() -> {
            CohortGrade g = new CohortGrade();
            g.cohortId = cohort.id;
            g.accountId = accountId;
            g.createdAt = Instant.now();
            return g;
        });
        grade.instructorId = cohort.instructorId;
        grade.riskManagementScore = req.riskManagementScore();
        grade.disciplineScore = req.disciplineScore();
        grade.diversificationScore = req.diversificationScore();
        grade.overallScore = req.overallScore();
        grade.feedback = req.feedback();
        grade.updatedAt = Instant.now();
        gradeRepo.save(grade);
        return gradeJson(grade);
    }

    private Map<String, Object> gradeJson(CohortGrade g) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("exists", true);
        m.put("riskManagementScore", g.riskManagementScore);
        m.put("disciplineScore", g.disciplineScore);
        m.put("diversificationScore", g.diversificationScore);
        m.put("overallScore", g.overallScore);
        m.put("feedback", g.feedback);
        m.put("updatedAt", g.updatedAt.toString());
        return m;
    }

    private Map<String, Object> orderJson(TradeOrder o, List<Fill> fills) {
        var instr = market.instrument(o.instrumentId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orderId", o.id);
        m.put("accountId", o.accountId);
        m.put("instrumentId", o.instrumentId);
        m.put("symbol", instr.symbol);
        m.put("instrumentType", instr.instrumentType.name());
        m.put("side", o.side.name());
        m.put("orderType", o.orderType.name());
        m.put("limitPrice", o.limitPrice);
        m.put("qty", o.qty);
        m.put("status", o.status.name());
        m.put("viewContext", o.viewContext == null ? null : o.viewContext.name());
        m.put("rejectReason", o.rejectReason);
        m.put("placedSimTime", o.placedSimTime == null ? null : o.placedSimTime.toString());
        m.put("fills", fills.stream().map(f -> Map.of(
                "price", f.price, "qty", f.qty, "simTime", f.fillSimTime.toString())).toList());
        return m;
    }

    private Map<String, Object> settlementJson(Settlement s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.id);
        m.put("simDate", s.simDate.toString());
        m.put("kind", s.kind.name());
        m.put("cashFlow", s.cashFlow);
        m.put("detail", s.detail);
        m.put("symbol", s.instrumentId == null ? null : market.instrument(s.instrumentId).symbol);
        return m;
    }
}
