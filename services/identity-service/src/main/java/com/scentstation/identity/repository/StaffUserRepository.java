package com.scentstation.identity.repository;

import com.scentstation.identity.model.StaffUser;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface StaffUserRepository extends JpaRepository<StaffUser, String> {
    Optional<StaffUser> findByUsername(String username);

    /** Serialize concurrent login attempts for one staff account. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from StaffUser u where u.username = :username")
    Optional<StaffUser> findByUsernameForLogin(@Param("username") String username);
}
