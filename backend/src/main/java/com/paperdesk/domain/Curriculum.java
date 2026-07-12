package com.paperdesk.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "curricula")
public class Curriculum {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "cohort_id")
    public Long cohortId;
    public String name;
    public String description;
    @Column(name = "created_at")
    public Instant createdAt = Instant.now();
}
