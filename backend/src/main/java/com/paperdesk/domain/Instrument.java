package com.paperdesk.domain;

import com.paperdesk.domain.Enums.CallPut;
import com.paperdesk.domain.Enums.InstrumentType;
import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "instruments")
public class Instrument {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "session_id")
    public Long sessionId;
    public String symbol;
    public String name;
    @Enumerated(EnumType.STRING)
    @Column(name = "instrument_type")
    public InstrumentType instrumentType;
    @Column(name = "underlying_id")
    public Long underlyingId;
    public Double strike;
    @Column(name = "expiry_date")
    public LocalDate expiryDate;
    @Enumerated(EnumType.STRING)
    @Column(name = "call_put")
    public CallPut callPut;
    @Column(name = "contract_size")
    public double contractSize = 1;
    @Column(name = "initial_margin")
    public Double initialMargin;
    @Column(name = "maintenance_margin")
    public Double maintenanceMargin;
    public Double notional;
    @Column(name = "fixed_rate")
    public Double fixedRate;
    @Column(name = "pay_freq_months")
    public Integer payFreqMonths;
    @Column(name = "term_months")
    public Integer termMonths;
    public boolean active = true;
}
