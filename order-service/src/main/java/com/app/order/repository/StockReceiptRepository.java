package com.app.order.repository;

import com.app.order.entity.StockReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockReceiptRepository extends JpaRepository<StockReceipt, String> {
}
