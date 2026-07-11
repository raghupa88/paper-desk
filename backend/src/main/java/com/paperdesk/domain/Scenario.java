package com.paperdesk.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "scenarios")
public class Scenario {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String name;
    public String description;
    public long seed;
    public double acceleration;
    @Lob
    @Column(name = "params_json")
    public String paramsJson;
}
