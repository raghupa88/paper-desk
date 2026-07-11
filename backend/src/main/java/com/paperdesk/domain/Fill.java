package com.paperdesk.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "fills")
public class Fill {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "order_id")
    public Long orderId;
    public double price;
    public double qty;
    @Column(name = "fill_sim_time")
    public Instant fillSimTime;
}
