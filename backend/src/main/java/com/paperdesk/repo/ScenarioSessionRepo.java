package com.paperdesk.repo;

import com.paperdesk.domain.ScenarioSession;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ScenarioSessionRepo extends JpaRepository<ScenarioSession, Long> {
    Optional<ScenarioSession> findFirstByScenarioIdAndCohortIdIsNull(Long scenarioId);
    List<ScenarioSession> findAll();
}
