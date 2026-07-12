package com.paperdesk.repo;

import com.paperdesk.domain.ClosedTrade;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ClosedTradeRepo extends JpaRepository<ClosedTrade, Long> {
    List<ClosedTrade> findByAccountIdOrderByClosedSimTimeDesc(Long accountId);
}
