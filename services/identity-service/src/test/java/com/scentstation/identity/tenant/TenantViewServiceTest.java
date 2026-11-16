package com.scentstation.identity.tenant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import com.scentstation.identity.audit.AuditService;
import com.scentstation.identity.rbac.AccessPolicy;
import com.scentstation.identity.rbac.AccountPrincipal;

class TenantViewServiceTest {
    private final TenantRepository repository = mock(TenantRepository.class);
    private final AccessPolicy access = mock(AccessPolicy.class);
    private final AuditService audit = mock(AuditService.class);
    private final TenantViewCache cache = mock(TenantViewCache.class);
    private final TenantService service = new TenantService(repository, access, audit);

    TenantViewServiceTest() { service.setTenantViewCache(cache); }

    @AfterEach void clearTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    private Tenant tenant() {
        Tenant t = new Tenant("brand", "Brand");
        ReflectionTestUtils.setField(t, "id", 5L);
        return t;
    }

    private void beginTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }

    private void commit() {
        TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCommit());
    }

    private void actor() {
        when(access.current()).thenReturn(new AccountPrincipal(1L, "platform", null,
            Set.of(), Set.of(), Set.of(), Set.of()));
    }

    @Test void cacheHitSkipsRepository() {
        TenantView hit = TenantView.from(tenant());
        when(cache.get(5L)).thenReturn(hit);
        assertEquals(hit, service.view(5L));
        verifyNoInteractions(repository);
    }

    @Test void cacheMissLoadsAndStoresView() {
        Tenant t = tenant();
        when(repository.findById(5L)).thenReturn(Optional.of(t));
        assertEquals("Brand", service.view(5L).name());
        verify(repository).findById(5L);
        verify(cache).put(eq(5L), eq(TenantView.from(t)));
    }

    @Test void missingTenantIsNotCached() {
        when(repository.findById(9L)).thenReturn(Optional.empty());
        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.view(9L));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(cache, never()).put(anyLong(), any());
    }

    @Test void platformUpdateEvictsOnlyAfterCommit() {
        when(repository.findById(5L)).thenReturn(Optional.of(tenant()));
        actor();
        beginTransaction();
        service.platformUpdate(5L, new UpdateTenantRequest("Changed", null));
        verify(cache, never()).evict(anyLong());
        commit();
        verify(cache).evict(5L);
        verify(cache, never()).evict(6L);
    }

    @Test void statusRollbackDoesNotEvict() {
        when(repository.findById(5L)).thenReturn(Optional.of(tenant()));
        actor();
        beginTransaction();
        service.status(5L, Tenant.Status.SUSPENDED);
        verify(cache, never()).evict(anyLong());
        // Rollback: do not invoke afterCommit.
    }
}
