package com.paperdesk.domain;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "daily_bars")
public class DailyBar {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "session_id")
    public Long sessionId;
    public String symbol;
    @Column(name = "sim_date")
    public LocalDate simDate;
    @Column(name = "open_px")
    public double open;
    @Column(name = "high_px")
    public double high;
    @Column(name = "low_px")
    public double low;
    @Column(name = "close_px")
    public double close;
}
