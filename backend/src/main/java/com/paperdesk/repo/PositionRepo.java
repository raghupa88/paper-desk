package com.paperdesk.repo;

import com.paperdesk.domain.Position;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface PositionRepo extends JpaRepository<Position, Long> {
    Optional<Position> findByAccountIdAndInstrumentId(Long accountId, Long instrumentId);
    List<Position> findByAccountId(Long accountId);
    List<Position> findByAccountIdAndQtyNot(Long accountId, double qty);

    @Query("select p from Position p where p.qty <> 0 and p.accountId in " +
           "(select a.id from Account a where a.sessionId = :sessionId)")
    List<Position> findOpenBySession(@Param("sessionId") Long sessionId);
}
