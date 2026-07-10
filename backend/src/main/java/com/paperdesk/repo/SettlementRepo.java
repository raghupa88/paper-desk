package com.paperdesk.repo;

import com.paperdesk.domain.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SettlementRepo extends JpaRepository<Settlement, Long> {
    List<Settlement> findByAccountIdOrderByIdDesc(Long accountId);
}
