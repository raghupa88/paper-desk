package com.paperdesk.repo;

import com.paperdesk.domain.ChallengeEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ChallengeEntryRepo extends JpaRepository<ChallengeEntry, Long> {
    List<ChallengeEntry> findByChallengeId(Long challengeId);
}
