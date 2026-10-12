package com.app.notification.repository;

import com.app.notification.entity.FulfillmentLog;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FulfillmentRepository extends JpaRepository<FulfillmentLog, String> {

    Optional<FulfillmentLog> findByOrderIdAndEventType(
            Long orderId,
            String eventType
    );
}
