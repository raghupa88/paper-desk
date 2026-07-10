package com.paperdesk.domain;

import com.paperdesk.domain.Enums.SessionState;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "scenario_sessions")
public class ScenarioSession {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "scenario_id")
    public Long scenarioId;
    @Column(name = "cohort_id")
    public Long cohortId;
    @Enumerated(EnumType.STRING)
    public SessionState state = SessionState.RUNNING;
    @Column(name = "sim_start")
    public Instant simStart;
    @Column(name = "current_sim_time")
    public Instant currentSimTime;
    public double acceleration;
    @Column(name = "created_at")
    public Instant createdAt = Instant.now();
}
