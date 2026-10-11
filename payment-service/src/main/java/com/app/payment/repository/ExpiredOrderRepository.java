package com.app.payment.repository;

import com.app.payment.entity.ExpiredOrder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpiredOrderRepository extends JpaRepository<ExpiredOrder, Long> {
}
