package com.paperdesk.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "cohort_grades")
public class CohortGrade {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "cohort_id")
    public Long cohortId;
    @Column(name = "account_id")
    public Long accountId;
    @Column(name = "instructor_id")
    public Long instructorId;
    @Column(name = "risk_management_score")
    public int riskManagementScore;
    @Column(name = "discipline_score")
    public int disciplineScore;
    @Column(name = "diversification_score")
    public int diversificationScore;
    @Column(name = "overall_score")
    public int overallScore;
    public String feedback;
    @Column(name = "created_at")
    public Instant createdAt;
    @Column(name = "updated_at")
    public Instant updatedAt;
}
