package com.paperdesk.repo;

import com.paperdesk.domain.TradeComment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TradeCommentRepo extends JpaRepository<TradeComment, Long> {
    List<TradeComment> findByOrderIdOrderByCreatedAt(Long orderId);
}
