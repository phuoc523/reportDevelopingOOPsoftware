package com.scentstation.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.scentstation.entity.Location;
import com.scentstation.repository.LocationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class LocationListInvalidationTest {
    @Test
    void locationCreateEvictsOnlyItsTenantListAfterCommit() {
        LocationRepository repository = mock(LocationRepository.class);
        LocationListCache cache = mock(LocationListCache.class);
        when(repository.existsById("lobby")).thenReturn(false);
        when(repository.save(any(Location.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LocationService service = new LocationService(repository);
        service.setLocationListCache(cache);

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.create("lobby", "tenant-17", "Lobby", null);

            verify(cache, never()).evict("tenant-17");
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);
            verify(cache).evict("tenant-17");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
