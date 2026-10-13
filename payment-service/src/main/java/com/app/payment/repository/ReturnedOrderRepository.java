package com.app.payment.repository;

import com.app.payment.entity.ReturnedOrder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReturnedOrderRepository extends JpaRepository<ReturnedOrder, Long> {
}
