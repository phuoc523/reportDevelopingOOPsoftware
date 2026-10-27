package com.scentstation.identity.platform;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "platform_settings", uniqueConstraints = @UniqueConstraint(name = "uk_setting_scope_key", columnNames = {"scope_id", "setting_key"}))
public class PlatformSetting {
    @Id @Column(length = 36) private String id = UUID.randomUUID().toString();
    @Column(name = "scope_id", nullable = false, length = 64) private String scopeId;
    @Column(name = "setting_key", nullable = false, length = 70) private String key;
    @Column(name = "setting_value", nullable = false, length = 200) private String value;
    @Column(name = "updated_by", nullable = false, length = 36) private String updatedBy;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected PlatformSetting() {}
    public PlatformSetting(String scopeId, String key, String value, String actorId, Instant when) {
        this.scopeId=scopeId;this.key=key;this.value=value;this.updatedBy=actorId;this.updatedAt=when;
    }
    public void update(String value, String actorId, Instant when){this.value=value;this.updatedBy=actorId;this.updatedAt=when;}
    public String getScopeId(){return scopeId;}
    public String getKey(){return key;}
    public String getValue(){return value;}
    public String getUpdatedBy(){return updatedBy;}
    public Instant getUpdatedAt(){return updatedAt;}
}
