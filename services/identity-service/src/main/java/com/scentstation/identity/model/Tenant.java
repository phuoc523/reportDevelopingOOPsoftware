package com.scentstation.identity.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Minimal tenant reference for P1-03; tenant CRUD belongs to P1-01. */
@Entity
@Table(name = "tenants")
public class Tenant {
    @Id
    @Column(length = 36, nullable = false)
    private String id = UUID.randomUUID().toString();

    @Column(nullable = false, unique = true, length = 80)
    private String code;

    @Column(nullable = false)
    private boolean active = true;

    protected Tenant() {}

    public Tenant(String code) { this.code = code; }

    public String getId() { return id; }
    public String getCode() { return code; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
