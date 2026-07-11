package com.paperdesk.repo;

import com.paperdesk.domain.Cohort;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CohortRepo extends JpaRepository<Cohort, Long> {
    Optional<Cohort> findByJoinCode(String joinCode);
    List<Cohort> findByInstructorId(Long instructorId);
}
