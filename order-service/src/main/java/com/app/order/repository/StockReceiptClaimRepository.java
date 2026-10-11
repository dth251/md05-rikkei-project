package com.app.order.repository;

import com.app.order.entity.StockReceipt;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

@Repository
public class StockReceiptClaimRepository {

    private final EntityManager entityManager;

    public StockReceiptClaimRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public void claim(StockReceipt receipt) {
        // Persist an assigned ID as a new row; merge could overwrite a concurrent winner.
        entityManager.persist(receipt);
        entityManager.flush();
    }
}
