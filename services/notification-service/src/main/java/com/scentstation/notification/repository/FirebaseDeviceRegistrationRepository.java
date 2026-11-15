package com.scentstation.notification.repository;

import com.scentstation.notification.entity.FirebaseDeviceRegistration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FirebaseDeviceRegistrationRepository extends JpaRepository<FirebaseDeviceRegistration, Long> {
    Optional<FirebaseDeviceRegistration> findByTenantIdAndUserIdAndDeviceId(
        String tenantId, String userId, String deviceId);

    Optional<FirebaseDeviceRegistration> findByFirebaseToken(String firebaseToken);

    List<FirebaseDeviceRegistration> findAllByTenantIdAndUserIdInAndActiveTrue(
        String tenantId, Collection<String> userIds);
}
