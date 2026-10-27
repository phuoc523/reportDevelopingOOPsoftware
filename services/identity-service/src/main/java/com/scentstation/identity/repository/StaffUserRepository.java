package com.scentstation.identity.repository;

import com.scentstation.identity.model.StaffUser;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.List;

public interface StaffUserRepository extends JpaRepository<StaffUser, String> {
    Optional<StaffUser> findByUsername(String username);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    List<StaffUser> findByTenant_Id(String tenantId);

    /** Tenant-filtered query prevents cross-tenant object lookup leaks. */
    @Query("select u from StaffUser u where u.id = :id and u.tenant.id = :tenantId")
    Optional<StaffUser> findWithinTenant(@Param("id") String id,
                                          @Param("tenantId") String tenantId);

    /** Serialize concurrent role updates to prevent lost writes and inconsistent audits. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from StaffUser u where u.id = :id")
    Optional<StaffUser> findByIdForRoleUpdate(@Param("id") String id);

    /** Serialize concurrent login attempts for one staff account. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from StaffUser u where u.username = :username")
    Optional<StaffUser> findByUsernameForLogin(@Param("username") String username);
}
