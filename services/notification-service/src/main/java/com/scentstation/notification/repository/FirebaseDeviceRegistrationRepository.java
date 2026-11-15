package com.scentstation.notification.repository;

import com.scentstation.notification.entity.FirebaseDeviceRegistration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FirebaseDeviceRegistrationRepository extends JpaRepository<FirebaseDeviceRegistration, Long> {
    Optional<FirebaseDeviceRegistration> findByTenantIdAndUserIdAndDeviceId(
        String tenantId, String userId, String deviceId);

    Optional<FirebaseDeviceRegistration> findByFirebaseToken(String firebaseToken);

    List<FirebaseDeviceRegistration> findAllByTenantIdAndUserIdInAndActiveTrue(
        String tenantId, Collection<String> userIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        update FirebaseDeviceRegistration registration
           set registration.active = false, registration.updatedAt = :updatedAt
         where registration.active = true
           and registration.firebaseToken in :tokens
        """)
    int deactivateActiveTokens(@Param("tokens") Collection<String> tokens,
                               @Param("updatedAt") Instant updatedAt);
}
