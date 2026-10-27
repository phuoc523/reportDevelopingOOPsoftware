package com.scentstation.identity.notifications;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface NotificationEventRepository extends JpaRepository<NotificationEvent,String>{
    Optional<NotificationEvent> findByTenantIdAndEventId(String tenantId,String eventId);
}
