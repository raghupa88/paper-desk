package com.paperdesk.repo;

import com.paperdesk.domain.MissionCompletion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MissionCompletionRepo extends JpaRepository<MissionCompletion, Long> {
    List<MissionCompletion> findByAccountId(Long accountId);
    boolean existsByAccountIdAndCode(Long accountId, String code);
}
