package com.paperdesk.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "trade_comments")
public class TradeComment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "order_id")
    public Long orderId;
    @Column(name = "account_id")
    public Long accountId;
    @Column(name = "instructor_id")
    public Long instructorId;
    public String comment;
    @Column(name = "created_at")
    public Instant createdAt;
}
