package com.paperdesk.domain;

import com.paperdesk.domain.Enums.SettlementKind;
import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "settlements")
public class Settlement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "account_id")
    public Long accountId;
    @Column(name = "instrument_id")
    public Long instrumentId;
    @Column(name = "sim_date")
    public LocalDate simDate;
    @Enumerated(EnumType.STRING)
    public SettlementKind kind;
    @Column(name = "cash_flow")
    public double cashFlow;
    public String detail;
}
