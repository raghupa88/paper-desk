package com.paperdesk.web;

import com.paperdesk.config.SecurityConfig;
import com.paperdesk.domain.Account;
import com.paperdesk.repo.AccountRepo;
import com.paperdesk.repo.CohortRepo;
import org.springframework.stereotype.Component;

@Component
public class AccountGuard {

    private final AccountRepo accountRepo;
    private final CohortRepo cohortRepo;

    public AccountGuard(AccountRepo accountRepo, CohortRepo cohortRepo) {
        this.accountRepo = accountRepo;
        this.cohortRepo = cohortRepo;
    }

    /** Loads the account and verifies it belongs to the authenticated user. */
    public Account owned(long accountId) {
        Account account = accountRepo.findById(accountId).orElseThrow();
        if (!account.userId.equals(SecurityConfig.currentUserId())) {
            throw new SecurityException("Account does not belong to you");
        }
        return account;
    }

    /** True if the authenticated user instructs the cohort this account's session belongs to. */
    public boolean instructs(Account account) {
        return cohortRepo.findBySessionId(account.sessionId)
                .map(c -> c.instructorId.equals(SecurityConfig.currentUserId()))
                .orElse(false);
    }

    /** Loads the account and verifies it's either owned by the caller or a cohort they instruct
     *  (read-only grading/feedback access — never used to authorize placing/cancelling orders). */
    public Account ownedOrInstructing(long accountId) {
        Account account = accountRepo.findById(accountId).orElseThrow();
        boolean owns = account.userId.equals(SecurityConfig.currentUserId());
        if (!owns && !instructs(account)) {
            throw new SecurityException("Not authorized for this account");
        }
        return account;
    }

    /** Loads the account and verifies the caller instructs its cohort. */
    public Account requireInstructing(long accountId) {
        Account account = accountRepo.findById(accountId).orElseThrow();
        if (!instructs(account)) {
            throw new SecurityException("Instructor of this cohort required");
        }
        return account;
    }
}
