package com.scentstation.identity.tenant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.scentstation.identity.audit.AuditService;
import com.scentstation.identity.rbac.AccountPrincipal;
import com.scentstation.identity.rbac.AccessPolicy;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class TenantProfileInvalidationTest {
    @Test
    void profileUpdateEvictsTenantProfileAfterCommit() {
        TenantRepository repository = mock(TenantRepository.class);
        AccessPolicy access = mock(AccessPolicy.class);
        AuditService audit = mock(AuditService.class);
        TenantProfileCache cache = mock(TenantProfileCache.class);
        Tenant tenant = mock(Tenant.class);
        AccountPrincipal actor = new AccountPrincipal(8L, "admin", 17L, Set.of(), Set.of(), Set.of(), Set.of());
        when(repository.findById(17L)).thenReturn(Optional.of(tenant));
        when(access.current()).thenReturn(actor);

        TenantService service = new TenantService(repository, access, audit);
        service.setProfileCache(cache);

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.platformUpdate(17L, new UpdateTenantRequest("Updated", null));

            verify(cache, never()).evict(17L);
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);
            verify(cache).evict(17L);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
