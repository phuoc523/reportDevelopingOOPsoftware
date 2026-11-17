package com.scentstation.identity.tenant;

import com.scentstation.identity.audit.AuditService;
import com.scentstation.identity.rbac.AccessPolicy;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TenantService {
    private final TenantRepository tenants;
    private final AccessPolicy access;
    private final AuditService audit;
    private TenantProfileCache profileCache;

    public TenantService(TenantRepository tenants, AccessPolicy access, AuditService audit) {
        this.tenants = tenants;
        this.access = access;
        this.audit = audit;
    }

    @Autowired(required = false)
    public void setProfileCache(TenantProfileCache profileCache) {
        this.profileCache = profileCache;
    }

    @Transactional(readOnly = true)
    public TenantView view(Long id) {
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tenant not assigned");
        }
        Tenant.Status liveStatus = tenants.findStatusById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
        TenantProfileSnapshot profile = profileCache == null ? null : profileCache.get(id);
        if (profile == null) {
            profile = TenantProfileSnapshot.from(require(id));
            if (profileCache != null) {
                profileCache.put(id, profile);
            }
        }
        return TenantView.from(profile, liveStatus);
    }

    private void evictProfileAfterCommit(Long tenantId) {
        if (profileCache == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    profileCache.evict(tenantId);
                }
            });
        } else {
            profileCache.evict(tenantId);
        }
    }

    public Tenant require(Long id) {
        if (id == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tenant not assigned");
        return tenants.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
    }

    public void requireActive(Long tenantId) {
        if (require(tenantId).getStatus() != Tenant.Status.ACTIVE)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tenant suspended");
    }

    public List<Tenant> all() { return tenants.findAll(); }

    @Transactional
    public Tenant create(CreateTenantRequest r) {
        String code = r.code().trim().toLowerCase(java.util.Locale.ROOT);
        if (tenants.existsByCode(code)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Tenant code already exists");
        Tenant t = new Tenant(code, r.name().trim());
        apply(t, r.profile());
        t = tenants.save(t);
        audit.record(access.current().id(), t.getId(), "TENANT_CREATED", "TENANT", t.getId().toString(), null, t.getStatus().name());
        return t;
    }

    @Transactional
    public Tenant platformUpdate(Long id, UpdateTenantRequest r) {
        Tenant t = require(id);
        if (r.name() != null && !r.name().isBlank()) t.setName(r.name().trim());
        apply(t, r.profile());
        audit.record(access.current().id(), id, "TENANT_UPDATED", "TENANT", id.toString(), null, "profile-changed");
        evictProfileAfterCommit(id);
        return t;
    }

    @Transactional
    public Tenant brandUpdate(UpdateTenantRequest r) {
        Long tenantId = access.current().tenantId();
        if (tenantId == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No brand tenant");
        requireActive(tenantId);
        Tenant t = require(tenantId);
        if (r.name() != null && !r.name().isBlank()) t.setName(r.name().trim());
        apply(t, r.profile());
        audit.record(access.current().id(), tenantId, "BRAND_PROFILE_UPDATED", "TENANT", tenantId.toString(), null, "profile-changed");
        evictProfileAfterCommit(tenantId);
        return t;
    }

    @Transactional
    public Tenant status(Long id, Tenant.Status status) {
        Tenant t = require(id);
        Tenant.Status previous = t.getStatus();
        if (previous == status) throw new ResponseStatusException(HttpStatus.CONFLICT, "Tenant already in requested status");
        t.setStatus(status);
        audit.record(access.current().id(), id, "TENANT_STATUS_CHANGED", "TENANT", id.toString(), previous.name(), status.name());
        evictProfileAfterCommit(id);
        return t;
    }

    private void apply(Tenant t, TenantProfile p) {
        if (p == null) return;
        if (p.logoUrl() != null) t.setLogoUrl(p.logoUrl());
        if (p.description() != null) t.setDescription(p.description());
        if (p.contactEmail() != null) t.setContactEmail(p.contactEmail());
        if (p.contactPhone() != null) t.setContactPhone(p.contactPhone());
        if (p.kioskContent() != null) t.setKioskContent(p.kioskContent());
    }
}
