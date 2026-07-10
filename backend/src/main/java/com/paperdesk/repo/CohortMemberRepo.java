package com.paperdesk.repo;

import com.paperdesk.domain.CohortMember;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CohortMemberRepo extends JpaRepository<CohortMember, Long> {
    List<CohortMember> findByCohortId(Long cohortId);
    List<CohortMember> findByUserId(Long userId);
    Optional<CohortMember> findByCohortIdAndUserId(Long cohortId, Long userId);
}
