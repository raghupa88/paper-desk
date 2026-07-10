package com.paperdesk.repo;

import com.paperdesk.domain.DailyBar;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface DailyBarRepo extends JpaRepository<DailyBar, Long> {
    List<DailyBar> findBySessionIdAndSymbolOrderBySimDate(Long sessionId, String symbol);
    boolean existsBySessionIdAndSymbolAndSimDate(Long sessionId, String symbol, LocalDate simDate);
}
