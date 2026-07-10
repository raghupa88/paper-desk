package com.paperdesk.repo;

import com.paperdesk.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AccountRepo extends JpaRepository<Account, Long> {
    Optional<Account> findByUserIdAndSessionId(Long userId, Long sessionId);
    List<Account> findByUserId(Long userId);
    List<Account> findBySessionId(Long sessionId);
}
