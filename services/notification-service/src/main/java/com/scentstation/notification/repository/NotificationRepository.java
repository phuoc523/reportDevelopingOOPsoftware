package com.scentstation.notification.repository;

import com.scentstation.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Optional<Notification> findByIdAndTenantId(Long id, String tenantId);

    boolean existsBySourceAndSourceEventId(String source, String sourceEventId);
}
