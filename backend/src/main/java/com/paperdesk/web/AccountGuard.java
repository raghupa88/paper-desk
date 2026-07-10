package com.paperdesk.web;

import com.paperdesk.config.SecurityConfig;
import com.paperdesk.domain.Account;
import com.paperdesk.repo.AccountRepo;
import org.springframework.stereotype.Component;

@Component
public class AccountGuard {

    private final AccountRepo accountRepo;

    public AccountGuard(AccountRepo accountRepo) {
        this.accountRepo = accountRepo;
    }

    /** Loads the account and verifies it belongs to the authenticated user. */
    public Account owned(long accountId) {
        Account account = accountRepo.findById(accountId).orElseThrow();
        if (!account.userId.equals(SecurityConfig.currentUserId())) {
            throw new SecurityException("Account does not belong to you");
        }
        return account;
    }
}
