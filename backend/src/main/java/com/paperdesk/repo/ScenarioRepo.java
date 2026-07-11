package com.paperdesk.repo;

import com.paperdesk.domain.Scenario;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ScenarioRepo extends JpaRepository<Scenario, Long> {
    Optional<Scenario> findByName(String name);
}
