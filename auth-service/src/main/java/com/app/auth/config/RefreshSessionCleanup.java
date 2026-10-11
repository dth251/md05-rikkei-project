package com.app.auth.config;

import com.app.auth.service.RefreshSessionTransactions;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class RefreshSessionCleanup {

    private final RefreshSessionTransactions transactions;

    public RefreshSessionCleanup(RefreshSessionTransactions transactions) {
        this.transactions = transactions;
    }

    @Scheduled(initialDelay = 3600000, fixedDelay = 3600000)
    public void cleanup() {
        transactions.cleanup();
    }
}
