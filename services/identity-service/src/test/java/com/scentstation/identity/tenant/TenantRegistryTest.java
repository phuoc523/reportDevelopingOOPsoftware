package com.scentstation.identity.tenant;

import com.scentstation.identity.repository.TenantRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TenantRegistryTest {

    private TenantRepository repository;
    private TenantRegistry registry;

    @BeforeEach
    void setUp() {
        repository = mock(TenantRepository.class);
        registry = new TenantRegistry(repository);
    }

    @Test
    void createTenantMatchesExistingSchema() {
        when(repository.existsByTenantCode("BRAND01")).thenReturn(false);
        when(repository.saveAndFlush(any(Tenant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Tenant result = registry.create(" BRAND01 ");

        assertNotNull(result.getId());
        assertEquals("BRAND01", result.getTenantCode());
        assertTrue(result.isActive());
        verify(repository).saveAndFlush(any(Tenant.class));
    }

    @Test
    void rejectDuplicateTenantCode() {
        when(repository.existsByTenantCode("BRAND01")).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> registry.create("BRAND01"));

        verify(repository, never()).saveAndFlush(any(Tenant.class));
    }

    @Test
    void rejectBlankTenantCode() {
        assertThrows(IllegalArgumentException.class,
                () -> registry.create(" "));

        verify(repository, never()).saveAndFlush(any(Tenant.class));
    }

    @Test
    void listTenantsByCode() {
        when(repository.findAllByOrderByTenantCodeAsc())
                .thenReturn(List.of(new Tenant("A"), new Tenant("B")));

        assertEquals(2, registry.findAll().size());
        verify(repository).findAllByOrderByTenantCodeAsc();
    }

    @Test
    void findTenantByStringId() {
        Tenant tenant = new Tenant("BRAND01");
        when(repository.findById(tenant.getId())).thenReturn(Optional.of(tenant));

        assertSame(tenant, registry.findById(tenant.getId()));
    }

    @Test
    void updateTenantCodeWithoutChangingId() {
        Tenant tenant = new Tenant("BRAND01");
        when(repository.findById(tenant.getId())).thenReturn(Optional.of(tenant));
        when(repository.existsByTenantCode("BRAND02")).thenReturn(false);
        when(repository.save(any(Tenant.class))).thenAnswer(invocation ->
                invocation.getArgument(0));

        Tenant updated = registry.updateCode(tenant.getId(), "BRAND02");

        assertEquals(tenant.getId(), updated.getId());
        assertEquals("BRAND02", updated.getTenantCode());
    }

    @Test
    void reportDuplicateCodeFromDatabase() {
        when(repository.existsByTenantCode("BRAND04")).thenReturn(false);
        when(repository.saveAndFlush(any(Tenant.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThrows(IllegalArgumentException.class,
                () -> registry.create("BRAND04"));
    }
}
