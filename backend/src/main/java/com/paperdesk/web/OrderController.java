package com.paperdesk.web;

import com.paperdesk.config.SecurityConfig;
import com.paperdesk.domain.Enums.OrderSide;
import com.paperdesk.domain.Enums.OrderType;
import com.paperdesk.domain.Enums.ViewContext;
import com.paperdesk.domain.Fill;
import com.paperdesk.domain.TradeComment;
import com.paperdesk.domain.TradeOrder;
import com.paperdesk.domain.User;
import com.paperdesk.repo.FillRepo;
import com.paperdesk.repo.OrderRepo;
import com.paperdesk.repo.TradeCommentRepo;
import com.paperdesk.repo.UserRepo;
import com.paperdesk.sim.MarketDataService;
import com.paperdesk.trading.OrderService;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    public record PlaceOrderRequest(long accountId, long instrumentId, OrderSide side, OrderType type,
                                    double qty, Double limitPrice, ViewContext viewContext) {}
    public record CancelRequest(long accountId) {}
    public record CommentRequest(String comment) {}

    private final OrderService orders;
    private final OrderRepo orderRepo;
    private final FillRepo fillRepo;
    private final TradeCommentRepo commentRepo;
    private final UserRepo userRepo;
    private final MarketDataService market;
    private final AccountGuard guard;

    public OrderController(OrderService orders, OrderRepo orderRepo, FillRepo fillRepo,
                           TradeCommentRepo commentRepo, UserRepo userRepo,
                           MarketDataService market, AccountGuard guard) {
        this.orders = orders;
        this.orderRepo = orderRepo;
        this.fillRepo = fillRepo;
        this.commentRepo = commentRepo;
        this.userRepo = userRepo;
        this.market = market;
        this.guard = guard;
    }

    @PostMapping
    public Map<String, Object> place(@RequestBody PlaceOrderRequest req) {
        guard.owned(req.accountId());
        TradeOrder order = orders.place(req.accountId(), req.instrumentId(), req.side(), req.type(),
                req.qty(), req.limitPrice(), req.viewContext());
        return orderJson(order, fillRepo.findByOrderIdIn(List.of(order.id)));
    }

    @PostMapping("/{orderId}/cancel")
    public Map<String, Object> cancel(@PathVariable long orderId, @RequestBody CancelRequest req) {
        guard.owned(req.accountId());
        TradeOrder order = orders.cancel(orderId, req.accountId());
        return orderJson(order, List.of());
    }

    /** Blotter: all orders of the account with their fills, newest first. */
    @GetMapping
    public List<Map<String, Object>> list(@RequestParam long accountId) {
        guard.owned(accountId);
        List<TradeOrder> all = orderRepo.findByAccountIdOrderByIdDesc(accountId);
        Map<Long, List<Fill>> fills = fillRepo
                .findByOrderIdIn(all.stream().map(o -> o.id).toList())
                .stream().collect(Collectors.groupingBy(f -> f.orderId));
        return all.stream().map(o -> orderJson(o, fills.getOrDefault(o.id, List.of()))).toList();
    }

    /** Comments on a trade -- readable by the student who placed it or the cohort's instructor. */
    @GetMapping("/{orderId}/comments")
    public List<Map<String, Object>> comments(@PathVariable long orderId) {
        TradeOrder order = orderRepo.findById(orderId).orElseThrow();
        guard.ownedOrInstructing(order.accountId);
        return commentRepo.findByOrderIdOrderByCreatedAt(orderId).stream().map(this::commentJson).toList();
    }

    /** Only the cohort's instructor may leave feedback on a student's trade. */
    @PostMapping("/{orderId}/comments")
    public Map<String, Object> addComment(@PathVariable long orderId, @RequestBody CommentRequest req) {
        TradeOrder order = orderRepo.findById(orderId).orElseThrow();
        guard.requireInstructing(order.accountId);
        TradeComment comment = new TradeComment();
        comment.orderId = orderId;
        comment.accountId = order.accountId;
        comment.instructorId = SecurityConfig.currentUserId();
        comment.comment = req.comment();
        comment.createdAt = Instant.now();
        commentRepo.save(comment);
        return commentJson(comment);
    }

    private Map<String, Object> commentJson(TradeComment c) {
        User instructor = userRepo.findById(c.instructorId).orElseThrow();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.id);
        m.put("orderId", c.orderId);
        m.put("instructorName", instructor.displayName);
        m.put("comment", c.comment);
        m.put("createdAt", c.createdAt.toString());
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
}
