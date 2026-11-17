package com.scentstation.catalog.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.scentstation.catalog.dto.request.CreateProductRequest;
import com.scentstation.catalog.entity.Product;
import com.scentstation.catalog.mapper.ProductMapper;
import com.scentstation.catalog.repository.ProductRepository;
import com.scentstation.catalog.security.CurrentActorContext;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class ProductListInvalidationTest {
    @Test
    void productCreateEvictsOnlyItsTenantListAfterCommit() {
        ProductRepository repository = mock(ProductRepository.class);
        ProductMapper mapper = mock(ProductMapper.class);
        CurrentActorContext actor = mock(CurrentActorContext.class);
        ProductListCache cache = mock(ProductListCache.class);
        CreateProductRequest request = CreateProductRequest.builder()
                .tenantId(17L)
                .name("Test product")
                .defaultPrice(BigDecimal.ONE)
                .build();
        Product product = Product.builder().tenantId(17L).name("Test product").build();
        when(actor.requireTenant(17L)).thenReturn(17L);
        when(mapper.toEntity(request, 17L)).thenReturn(product);
        when(repository.save(product)).thenReturn(product);

        ProductService service = new ProductService(repository, mapper, actor);
        service.setProductListCache(cache);

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.createProduct(request);

            verify(cache, never()).evict(17L);
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);
            verify(cache).evict(17L);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
