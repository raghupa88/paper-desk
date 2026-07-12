package com.paperdesk.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "challenges")
public class Challenge {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "cohort_id")
    public Long cohortId;
    public String name;
    @Column(name = "duration_sim_days")
    public int durationSimDays;
    @Column(name = "start_sim_date")
    public LocalDate startSimDate;
    @Column(name = "end_sim_date")
    public LocalDate endSimDate;
    @Column(name = "created_at")
    public Instant createdAt = Instant.now();
}
