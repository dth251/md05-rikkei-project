package com.app.payment.repository;

import com.app.payment.entity.VnpayRefund;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VnpayRefundRepository extends JpaRepository<VnpayRefund, Long> {
}
