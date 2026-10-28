package com.scentstation.identity.tenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scentstation.identity.repository.TenantRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
        registry = new TenantRegistry(repository, new ObjectMapper());
    }

    @Test
    void createTenantWithDefaultConfiguration() {
        when(repository.existsByTenantCode("BRAND01")).thenReturn(false);
        when(repository.saveAndFlush(any(Tenant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Tenant result = registry.create("BRAND01", "Tenant One", "Brand One", null);

        assertEquals("BRAND01", result.getTenantCode());
        assertEquals("Tenant One", result.getName());
        assertEquals("Brand One", result.getBrandName());
        assertEquals("{}", result.getConfigurationJson());
        verify(repository).saveAndFlush(any(Tenant.class));
    }

    @Test
    void createTenantWithCustomConfiguration() {
        when(repository.existsByTenantCode("BRAND02")).thenReturn(false);
        when(repository.saveAndFlush(any(Tenant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Tenant result = registry.create(
                "BRAND02", "Tenant Two", "Brand Two", "{\"theme\":\"dark\"}");

        assertEquals("{\"theme\":\"dark\"}", result.getConfigurationJson());
    }

    @Test
    void rejectDuplicateTenantCode() {
        when(repository.existsByTenantCode("BRAND01")).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> registry.create("BRAND01", "Tenant One", "Brand One", "{}"));

        verify(repository, never()).saveAndFlush(any(Tenant.class));
    }

    @Test
    void rejectInvalidConfigurationJson() {
        assertThrows(IllegalArgumentException.class,
                () -> registry.create("BRAND03", "Tenant Three", "Brand Three", "{invalid"));

        verify(repository, never()).saveAndFlush(any(Tenant.class));
    }

    @Test
    void listTenantsByName() {
        List<Tenant> tenants = List.of(
                new Tenant("B", "Beta", "Beta Brand"),
                new Tenant("A", "Alpha", "Alpha Brand"));
        when(repository.findAllByOrderByNameAsc()).thenReturn(tenants);

        assertEquals(2, registry.findAll().size());
        verify(repository).findAllByOrderByNameAsc();
    }

    @Test
    void throwWhenTenantDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThrows(java.util.NoSuchElementException.class,
                () -> registry.findById(id));
    }

    @Test
    void rejectDuplicateCodeReportedByDatabase() {
        when(repository.existsByTenantCode("BRAND04")).thenReturn(false);
        when(repository.saveAndFlush(any(Tenant.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThrows(IllegalArgumentException.class,
                () -> registry.create("BRAND04", "Tenant Four", "Brand Four", "{}"));
    }
}
