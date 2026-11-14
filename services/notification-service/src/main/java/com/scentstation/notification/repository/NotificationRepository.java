package com.scentstation.notification.repository;

import com.scentstation.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Optional<Notification> findByIdAndTenantId(Long id, String tenantId);

    Page<Notification> findAllByTenantIdOrderByCreatedAtDescIdDesc(String tenantId, Pageable pageable);

    @Query("""
        select n from Notification n
        where n.tenantId = :tenantId
          and (:notificationType is null or n.notificationType = :notificationType)
          and (:priority is null or n.priority = :priority)
        """)
    Page<Notification> findPageByTenant(
        @Param("tenantId") String tenantId,
        @Param("notificationType") String notificationType,
        @Param("priority") String priority,
        Pageable pageable
    );

    boolean existsBySourceAndSourceEventId(String source, String sourceEventId);
}
