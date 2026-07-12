package com.paperdesk.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "curriculum_steps")
public class CurriculumStep {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "curriculum_id")
    public Long curriculumId;
    @Column(name = "mission_code")
    public String missionCode;
    @Column(name = "step_order")
    public int stepOrder;
}
