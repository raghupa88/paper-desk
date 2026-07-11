package com.paperdesk.domain;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "equity_snapshots")
public class EquitySnapshot {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "account_id")
    public Long accountId;
    @Column(name = "sim_date")
    public LocalDate simDate;
    public double equity;
}
