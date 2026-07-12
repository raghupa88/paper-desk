package com.paperdesk.repo;

import com.paperdesk.domain.Challenge;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ChallengeRepo extends JpaRepository<Challenge, Long> {
    List<Challenge> findByCohortIdOrderByIdDesc(Long cohortId);
}
