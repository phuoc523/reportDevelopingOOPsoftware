package com.scentstation.identity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class StaffUserRoleId implements Serializable {

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "role_name", nullable = false, length = 64)
    private String roleName;

    protected StaffUserRoleId() {
    }

    public StaffUserRoleId(String userId, String roleName) {
        this.userId = userId;
        this.roleName = roleName;
    }

    public String getUserId() { return userId; }
    public String getRoleName() { return roleName; }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof StaffUserRoleId that)) {
            return false;
        }
        return Objects.equals(userId, that.userId)
                && Objects.equals(roleName, that.roleName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, roleName);
    }
}
