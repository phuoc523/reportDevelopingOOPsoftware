package com.scentstation.identity.entity;

import com.scentstation.identity.rbac.Permission;
import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 80)
    private String username;
    @Column(nullable = false, unique = true, length = 160)
    private String email;
    @Column(nullable = false)
    private String password;
    @Column(nullable = false, length = 120)
    private String fullName;
    /** Retained for compatibility with existing records and old API consumers. */
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40)
    private Role role;
    @Column(nullable = false)
    private boolean active = true;
    @Column(nullable = false)
    private boolean locked = false;
    @Column(name = "tenant_id")
    private Long tenantId;
    @Column(nullable = false)
    private long tokenVersion = 0;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"),
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "role_name"}))
    @Enumerated(EnumType.STRING) @Column(name = "role_name", nullable = false, length = 48)
    private Set<Role> roles = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_permission_grants", joinColumns = @JoinColumn(name = "user_id"),
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "permission_name"}))
    @Enumerated(EnumType.STRING) @Column(name = "permission_name", nullable = false, length = 64)
    private Set<Permission> permissionGrants = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_location_scopes", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "location_id", nullable = false)
    private Set<Long> locationIds = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_machine_scopes", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "machine_id", nullable = false)
    private Set<Long> machineIds = new HashSet<>();

    protected User() {}

    public User(String username, String email, String password, String fullName, Role role) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.fullName = fullName;
        this.role = role;
        this.roles.add(role);
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPassword() { return password; }
    public String getFullName() { return fullName; }
    public Role getRole() { return role; }
    public boolean isActive() { return active; }
    public boolean isLocked() { return locked; }
    public Long getTenantId() { return tenantId; }
    public long getTokenVersion() { return tokenVersion; }
    public Set<Role> getRoles() { return roles == null ? Set.of() : roles; }
    public Set<Permission> getPermissionGrants() { return permissionGrants == null ? Set.of() : permissionGrants; }
    public Set<Long> getLocationIds() { return locationIds == null ? Set.of() : locationIds; }
    public Set<Long> getMachineIds() { return machineIds == null ? Set.of() : machineIds; }

    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public void setActive(boolean active) { this.active = active; revokeSessions(); }
    public void setLocked(boolean locked) { this.locked = locked; revokeSessions(); }
    public void setPassword(String hash) { this.password = hash; revokeSessions(); }
    public void setEmail(String email) { this.email = email; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public void setRoles(Set<Role> roles) {
        if (roles == null || roles.isEmpty()) throw new IllegalArgumentException("At least one role is required");
        this.roles = new HashSet<>(roles);
        this.role = roles.stream().sorted().findFirst().orElseThrow();
        revokeSessions();
    }
    public void setPermissionGrants(Set<Permission> grants) {
        this.permissionGrants = new HashSet<>(grants);
        revokeSessions();
    }
    public void setLocationIds(Set<Long> ids) { this.locationIds = new HashSet<>(ids); revokeSessions(); }
    public void setMachineIds(Set<Long> ids) { this.machineIds = new HashSet<>(ids); revokeSessions(); }
    public void revokeSessions() { tokenVersion++; }
}
