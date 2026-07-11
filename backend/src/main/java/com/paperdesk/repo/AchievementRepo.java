package com.paperdesk.repo;

import com.paperdesk.domain.Achievement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;

public interface AchievementRepo extends JpaRepository<Achievement, Long> {
    List<Achievement> findByAccountId(Long accountId);
    boolean existsByAccountIdAndCode(Long accountId, String code);
    long countByAccountIdAndCodeIn(Long accountId, Collection<String> codes);
}
