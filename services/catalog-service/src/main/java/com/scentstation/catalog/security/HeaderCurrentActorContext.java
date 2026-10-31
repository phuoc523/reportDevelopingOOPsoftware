package com.scentstation.catalog.security;

import com.scentstation.catalog.exception.TrustedActorContextException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequestScope
public class HeaderCurrentActorContext implements CurrentActorContext {

    public static final String USER_HEADER = "X-Verified-User-Id";
    public static final String TENANT_HEADER = "X-Verified-Tenant-Id";
    public static final String ROLES_HEADER = "X-Verified-Roles";

    private final HttpServletRequest request;

    // Tạo context từ các header nội bộ mà Gateway phải strip rồi overwrite.
    public HeaderCurrentActorContext(HttpServletRequest request) {
        this.request = request;
    }

    // Lấy actor đã được Gateway xác minh; actor có thể chưa được cung cấp ở giai đoạn tích hợp tạm thời.
    @Override
    public String getActorId() {
        return request.getHeader(USER_HEADER);
    }

    // Lấy tenant số của Catalog từ header nội bộ và fail closed khi header thiếu hoặc sai định dạng.
    @Override
    public Long getTenantId() {
        String value = request.getHeader(TENANT_HEADER);

        try {
            long tenantId = Long.parseLong(value);
            if (tenantId <= 0) {
                throw new NumberFormatException();
            }
            return tenantId;
        } catch (NumberFormatException | NullPointerException exception) {
            throw new TrustedActorContextException(
                    "Missing or invalid verified tenant context"
            );
        }
    }

    // Tách danh sách role đã được Gateway xác minh từ header nội bộ.
    @Override
    public Set<String> getRoles() {
        String value = request.getHeader(ROLES_HEADER);
        if (value == null || value.isBlank()) {
            return Set.of();
        }

        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
