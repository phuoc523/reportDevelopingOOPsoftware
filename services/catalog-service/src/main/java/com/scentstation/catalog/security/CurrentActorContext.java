package com.scentstation.catalog.security;

import com.scentstation.catalog.exception.TenantClaimMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

public interface CurrentActorContext {

    // Lấy mã người dùng đã được authentication boundary xác minh, nếu có.
    String getActorId();

    // Lấy tenant Catalog đã được authentication boundary xác minh.
    Long getTenantId();

    // Lấy các vai trò đã được authentication boundary xác minh.
    Set<String> getRoles();

    Set<String> getPermissions();

    default void requirePermission(String permission) {
        if (!getPermissions().contains(permission)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Catalog permission required");
        }
    }

    // Trả tenant đã xác minh và từ chối tenant claim khác nếu request cũ vẫn gửi claim.
    default Long requireTenant(Long claimedTenantId) {
        Long tenantId = getTenantId();
        if (tenantId == null) {
            if (getRoles().contains("PLATFORM_SUPER_ADMIN")
                    && claimedTenantId != null && claimedTenantId > 0) {
                return claimedTenantId;
            }
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Platform actor must provide an explicit target tenant"
            );
        }
        if (claimedTenantId != null && !tenantId.equals(claimedTenantId)) {
            throw new TenantClaimMismatchException();
        }
        return tenantId;
    }
}
