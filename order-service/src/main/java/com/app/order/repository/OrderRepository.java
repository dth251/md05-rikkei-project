package com.app.order.repository;

import com.app.order.entity.PurchaseOrder;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<PurchaseOrder, Long> {

    Optional<PurchaseOrder> findByUserIdAndIdempotencyKey(
            Long userId,
            String idempotencyKey
    );
}
