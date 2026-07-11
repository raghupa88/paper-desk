package com.paperdesk.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "positions")
public class Position {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "account_id")
    public Long accountId;
    @Column(name = "instrument_id")
    public Long instrumentId;
    public double qty;
    @Column(name = "avg_price")
    public double avgPrice;
    @Column(name = "realized_pnl")
    public double realizedPnl;
    @Column(name = "last_settle_price")
    public Double lastSettlePrice;
    @Column(name = "opened_sim_time")
    public Instant openedSimTime;
}
