package com.scentstation.catalog.service;

import com.scentstation.catalog.entity.Product;
import com.scentstation.catalog.entity.RetailSaleCommit;
import com.scentstation.catalog.exception.ProductNotFoundException;
import com.scentstation.catalog.repository.ProductRepository;
import com.scentstation.catalog.repository.RetailSaleCommitRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RetailSaleService {
    private final ProductRepository products;
    private final RetailSaleCommitRepository commits;
    private ProductListCache productListCache;

    public RetailSaleService(ProductRepository products, RetailSaleCommitRepository commits) {
        this.products = products;
        this.commits = commits;
    }

    @Autowired(required = false)
    public void setProductListCache(ProductListCache productListCache) {
        this.productListCache = productListCache;
    }

    @Transactional
    public void commit(Long tenantId, Long productId, Long orderId, int quantity) {
        if (commits.existsById(orderId)) return;
        Product product = products.findForUpdateByIdAndTenantId(productId, tenantId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        int available = product.getBottleStock() == null ? 0 : product.getBottleStock();
        if (product.getBottlePrice() == null || available < quantity) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Retail bottle stock is insufficient");
        }
        product.setBottleStock(available - quantity);
        products.save(product);
        commits.save(new RetailSaleCommit(orderId, tenantId, productId, quantity));
        evictProductListAfterCommit(tenantId);
    }

    private void evictProductListAfterCommit(Long tenantId) {
        if (productListCache == null) return;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    productListCache.evict(tenantId);
                }
            });
        } else {
            productListCache.evict(tenantId);
        }
    }
}
