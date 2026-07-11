package com.paperdesk.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "mission_completions")
public class MissionCompletion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "account_id")
    public Long accountId;
    public String code;
    public int xp;
    @Column(name = "sim_date")
    public LocalDate simDate;
    @Column(name = "created_at")
    public Instant createdAt = Instant.now();
}
