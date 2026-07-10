package com.paperdesk.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "accounts")
public class Account {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "user_id")
    public Long userId;
    @Column(name = "session_id")
    public Long sessionId;
    @Column(name = "cash_balance")
    public double cashBalance;
    @Column(name = "margin_held")
    public double marginHeld;
    @Column(name = "starting_balance")
    public double startingBalance;
    @Column(name = "created_at")
    public Instant createdAt = Instant.now();
}
