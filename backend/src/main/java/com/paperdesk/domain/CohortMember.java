package com.paperdesk.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "cohort_members")
public class CohortMember {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "cohort_id")
    public Long cohortId;
    @Column(name = "user_id")
    public Long userId;
}
