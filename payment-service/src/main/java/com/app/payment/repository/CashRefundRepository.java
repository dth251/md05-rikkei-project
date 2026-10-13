package com.app.payment.repository;

import com.app.payment.entity.CashRefund;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CashRefundRepository extends JpaRepository<CashRefund, Long> {
}
