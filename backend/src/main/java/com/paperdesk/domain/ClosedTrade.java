package com.paperdesk.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "closed_trades")
public class ClosedTrade {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "account_id")
    public Long accountId;
    @Column(name = "instrument_id")
    public Long instrumentId;
    @Column(name = "opened_sim_time")
    public Instant openedSimTime;
    @Column(name = "closed_sim_time")
    public Instant closedSimTime;
    public double qty;
    @Column(name = "realized_pnl")
    public double realizedPnl;
}
