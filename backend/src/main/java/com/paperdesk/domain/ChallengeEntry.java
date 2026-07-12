package com.paperdesk.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "challenge_entries")
public class ChallengeEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "challenge_id")
    public Long challengeId;
    @Column(name = "account_id")
    public Long accountId;
    @Column(name = "starting_equity")
    public double startingEquity;
}
