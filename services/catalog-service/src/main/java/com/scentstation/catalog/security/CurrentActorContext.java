package com.scentstation.catalog.security;

import com.scentstation.catalog.exception.TenantClaimMismatchException;

import java.util.Set;

public interface CurrentActorContext {

    // Lấy mã người dùng đã được authentication boundary xác minh, nếu có.
    String getActorId();

    // Lấy tenant Catalog đã được authentication boundary xác minh.
    Long getTenantId();

    // Lấy các vai trò đã được authentication boundary xác minh.
    Set<String> getRoles();

    // Trả tenant đã xác minh và từ chối tenant claim khác nếu request cũ vẫn gửi claim.
    default Long requireTenant(Long claimedTenantId) {
        Long tenantId = getTenantId();
        if (claimedTenantId != null && !tenantId.equals(claimedTenantId)) {
            throw new TenantClaimMismatchException();
        }
        return tenantId;
    }
}
