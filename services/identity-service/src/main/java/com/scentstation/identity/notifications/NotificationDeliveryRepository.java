package com.scentstation.identity.notifications;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import org.springframework.data.domain.Pageable;
public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery,String>{
    List<NotificationDelivery> findByTenantIdAndUserIdOrderByCreatedAtDesc(String tenantId,String userId,Pageable page);
}
