package com.paperdesk.repo;

import com.paperdesk.domain.Enums.OrderStatus;
import com.paperdesk.domain.TradeOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface OrderRepo extends JpaRepository<TradeOrder, Long> {
    List<TradeOrder> findByAccountIdOrderByIdDesc(Long accountId);

    @Query("select o from TradeOrder o where o.status = :status and o.accountId in " +
           "(select a.id from Account a where a.sessionId = :sessionId)")
    List<TradeOrder> findOpenBySession(@Param("status") OrderStatus status, @Param("sessionId") Long sessionId);
}
