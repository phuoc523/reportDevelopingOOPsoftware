package com.scentstation.identity.repository;

import com.scentstation.identity.entity.StaffUserRole;
import com.scentstation.identity.entity.StaffUserRoleId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffUserRoleRepository
        extends JpaRepository<StaffUserRole, StaffUserRoleId> {

    List<StaffUserRole> findAllByIdUserId(String userId);

    void deleteAllByIdUserId(String userId);
}
