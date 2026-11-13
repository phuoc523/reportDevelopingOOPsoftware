package com.scentstation.notification.repository;

import com.scentstation.notification.entity.NotificationRecipient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NotificationRecipientRepository extends JpaRepository<NotificationRecipient, Long> {

    Page<NotificationRecipient> findAllByNotification_TenantIdAndRecipientIdOrderByCreatedAtDesc(
        String tenantId,
        String recipientId,
        Pageable pageable
    );

    Optional<NotificationRecipient> findByNotification_IdAndNotification_TenantIdAndRecipientId(
        Long notificationId,
        String tenantId,
        String recipientId
    );

    long countByNotification_TenantIdAndRecipientIdAndReadAtIsNull(String tenantId, String recipientId);
}
