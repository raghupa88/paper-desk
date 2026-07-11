package com.paperdesk.domain;

import com.paperdesk.domain.Enums.Role;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "users")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String email;
    @Column(name = "password_hash")
    public String passwordHash;
    @Column(name = "display_name")
    public String displayName;
    @Enumerated(EnumType.STRING)
    public Role role;
    @Column(name = "created_at")
    public Instant createdAt = Instant.now();
    @Column(name = "last_active_date")
    public LocalDate lastActiveDate;
    @Column(name = "current_streak")
    public int currentStreak;
    @Column(name = "longest_streak")
    public int longestStreak;
}
