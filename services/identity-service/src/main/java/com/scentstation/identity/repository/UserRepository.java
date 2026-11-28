package com.scentstation.identity.repository;

import com.scentstation.identity.entity.User;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    List<User> findByTenantId(Long tenantId);
    List<User> findByTenantIdOrTenantIdIsNull(Long tenantId);
}
