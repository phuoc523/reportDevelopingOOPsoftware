package com.scentstation.identity.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.scentstation.identity.audit.AuditService;
import com.scentstation.identity.rbac.AccessPolicy;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TenantViewLiveStatusTest {
    @Test
    void cachedDisplayProfileNeverSuppliesTenantStatus() {
        TenantRepository repository = mock(TenantRepository.class);
        TenantProfileCache cache = mock(TenantProfileCache.class);
        TenantService service = new TenantService(repository, mock(AccessPolicy.class), mock(AuditService.class));
        service.setProfileCache(cache);
        TenantProfileSnapshot cached = new TenantProfileSnapshot(
                11L, "brand", "Cached name", null, "display", null, null, null);
        when(repository.findStatusById(11L)).thenReturn(Optional.of(Tenant.Status.SUSPENDED));
        when(cache.get(11L)).thenReturn(cached);

        TenantView view = service.view(11L);

        assertEquals("Cached name", view.name());
        assertEquals(Tenant.Status.SUSPENDED, view.status());
        verify(repository).findStatusById(11L);
    }
}
