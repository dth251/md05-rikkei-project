package com.app.order.repository;

import com.app.order.entity.PurchaseOrder;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<PurchaseOrder, Long>, JpaSpecificationExecutor<PurchaseOrder> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select purchaseOrder from PurchaseOrder purchaseOrder where purchaseOrder.id = :id")
    Optional<PurchaseOrder> findLockedById(@Param("id") Long id);

    Optional<PurchaseOrder> findByUserIdAndIdempotencyKey(
            Long userId,
            String idempotencyKey
    );
}
