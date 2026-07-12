package com.paperdesk.repo;

import com.paperdesk.domain.CohortGrade;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CohortGradeRepo extends JpaRepository<CohortGrade, Long> {
    Optional<CohortGrade> findByCohortIdAndAccountId(Long cohortId, Long accountId);
}
