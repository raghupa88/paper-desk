package com.paperdesk.domain;

import com.paperdesk.domain.Enums.*;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "orders")
public class TradeOrder {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "account_id")
    public Long accountId;
    @Column(name = "instrument_id")
    public Long instrumentId;
    @Enumerated(EnumType.STRING)
    public OrderSide side;
    @Enumerated(EnumType.STRING)
    @Column(name = "order_type")
    public OrderType orderType;
    @Column(name = "limit_price")
    public Double limitPrice;
    public double qty;
    @Enumerated(EnumType.STRING)
    public OrderStatus status = OrderStatus.NEW;
    @Enumerated(EnumType.STRING)
    @Column(name = "view_context")
    public ViewContext viewContext;
    @Column(name = "reject_reason")
    public String rejectReason;
    @Column(name = "placed_sim_time")
    public Instant placedSimTime;
}
