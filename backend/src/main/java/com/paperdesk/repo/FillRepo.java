package com.paperdesk.repo;

import com.paperdesk.domain.Fill;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;

public interface FillRepo extends JpaRepository<Fill, Long> {
    List<Fill> findByOrderIdIn(Collection<Long> orderIds);
}
