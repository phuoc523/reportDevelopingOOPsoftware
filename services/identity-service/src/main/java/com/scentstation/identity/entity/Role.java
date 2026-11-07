package com.scentstation.identity.entity;

/** Persisted role names. Legacy values stay readable for existing accounts. */
public enum Role {
    PLATFORM_SUPER_ADMIN,
    BRAND_ADMIN,
    OPERATIONS_MANAGER,
    TECHNICIAN,
    INVENTORY_STAFF,
    VIEWER,
    CUSTOMER,
    /** Legacy roles: not granted brand/platform privileges by default. */
    ADMIN,
    STAFF
}
