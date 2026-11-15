package com.scentstation.identity.repository;

import com.scentstation.identity.entity.StaffUser;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StaffUserRepository extends JpaRepository<StaffUser, String> {

    @EntityGraph(attributePaths = "roles")
    Optional<StaffUser> findByUsername(String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "roles")
    @Query("select staff from StaffUser staff where staff.username = :username")
    Optional<StaffUser> findByUsernameForUpdate(@Param("username") String username);

    boolean existsByUsername(String username);

    boolean existsByUsernameAndIdNot(String username, String id);

    @EntityGraph(attributePaths = "roles")
    List<StaffUser> findAllByOrderByUsernameAsc();

    @EntityGraph(attributePaths = "roles")
    List<StaffUser> findAllByTenantIdOrderByUsernameAsc(String tenantId);

    @EntityGraph(attributePaths = "roles")
    Optional<StaffUser> findWithRolesById(String id);

    @EntityGraph(attributePaths = "roles")
    Optional<StaffUser> findWithRolesByIdAndTenantId(String id, String tenantId);
}
