package com.scentstation.identity.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

@Entity
@Table(name = "staff_user_roles")
public class StaffUserRole {

    @EmbeddedId
    private StaffUserRoleId id;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private StaffUser staffUser;

    protected StaffUserRole() {
    }

    public StaffUserRole(StaffUserRoleId id, StaffUser staffUser) {
        this.id = id;
        this.staffUser = staffUser;
    }

    public StaffUserRoleId getId() { return id; }
    public StaffUser getStaffUser() { return staffUser; }
}
