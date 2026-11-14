package com.scentstation.notification.repository;

import com.scentstation.notification.entity.NotificationRecipient;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NotificationRecipientRepository extends JpaRepository<NotificationRecipient, Long> {

    Page<NotificationRecipient> findAllByNotification_TenantIdAndRecipientIdOrderByCreatedAtDesc(
        String tenantId,
        String recipientId,
        Pageable pageable
    );

    @Query("""
        select r from NotificationRecipient r
        where r.notification.tenantId = :tenantId
          and r.recipientId = :recipientId
          and (:notificationType is null or r.notification.notificationType = :notificationType)
          and (:priority is null or r.notification.priority = :priority)
        """)
    Page<NotificationRecipient> findInboxPage(
        @Param("tenantId") String tenantId,
        @Param("recipientId") String recipientId,
        @Param("notificationType") String notificationType,
        @Param("priority") String priority,
        Pageable pageable
    );

    Optional<NotificationRecipient> findByNotification_IdAndNotification_TenantIdAndRecipientId(
        Long notificationId,
        String tenantId,
        String recipientId
    );

    long countByNotification_TenantIdAndRecipientIdAndReadAtIsNull(String tenantId, String recipientId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update NotificationRecipient r
        set r.readAt = :readAt, r.updatedAt = :readAt
        where r.notification.tenantId = :tenantId
          and r.recipientId = :recipientId
          and r.readAt is null
          and r.notification.id in (
              select n.id from Notification n
              where n.tenantId = :tenantId and n.id in :notificationIds
          )
        """)
    int markUnreadNotificationsAsRead(
        @Param("tenantId") String tenantId,
        @Param("recipientId") String recipientId,
        @Param("notificationIds") List<Long> notificationIds,
        @Param("readAt") Instant readAt
    );
}
