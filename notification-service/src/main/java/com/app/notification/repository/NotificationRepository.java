package com.app.notification.repository;

import com.app.notification.entity.NotificationLog;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<NotificationLog, String> {

    Optional<NotificationLog> findByOrderId(Long orderId);
}
