package com.paperdesk.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "cohorts")
public class Cohort {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String name;
    @Column(name = "instructor_id")
    public Long instructorId;
    @Column(name = "scenario_id")
    public Long scenarioId;
    @Column(name = "session_id")
    public Long sessionId;
    @Column(name = "starting_balance")
    public double startingBalance;
    @Column(name = "join_code")
    public String joinCode;
    @Column(name = "created_at")
    public Instant createdAt = Instant.now();
}
