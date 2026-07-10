package com.paperdesk.repo;

import com.paperdesk.domain.EquitySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface EquitySnapshotRepo extends JpaRepository<EquitySnapshot, Long> {
    Optional<EquitySnapshot> findFirstByAccountIdOrderBySimDateDesc(Long accountId);
    List<EquitySnapshot> findByAccountIdOrderBySimDate(Long accountId);
}
