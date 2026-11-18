package com.scentstation.catalog.controller;

import com.scentstation.catalog.dto.response.EffectivePriceResponse;
import com.scentstation.catalog.dto.response.OrderProductSnapshotResponse;
import com.scentstation.catalog.dto.request.CommitRetailSaleRequest;
import com.scentstation.catalog.entity.Product;
import com.scentstation.catalog.exception.ProductNotFoundException;
import com.scentstation.catalog.repository.ProductRepository;
import com.scentstation.catalog.service.InternalServiceKeyVerifier;
import com.scentstation.catalog.service.PromotionalPricingService;
import com.scentstation.catalog.service.RetailSaleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/catalog/order-products")
public class InternalOrderProductController {
    private final ProductRepository products;
    private final PromotionalPricingService pricing;
    private final InternalServiceKeyVerifier keys;
    private final RetailSaleService retailSales;

    public InternalOrderProductController(ProductRepository products, PromotionalPricingService pricing,
                                          InternalServiceKeyVerifier keys, RetailSaleService retailSales) {
        this.products = products;
        this.pricing = pricing;
        this.keys = keys;
        this.retailSales = retailSales;
    }

    @GetMapping("/{productId}")
    public OrderProductSnapshotResponse get(@PathVariable Long productId, @RequestParam Long tenantId,
                                            @RequestHeader(value = "X-Internal-Service-Key", required = false) String key) {
        keys.verify(key);
        Product product = products.findByIdAndTenantId(productId, tenantId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        EffectivePriceResponse price = pricing.getInternalEffectivePrice(product);
        return new OrderProductSnapshotResponse(product.getId(), product.getTenantId(), product.getName(),
                product.getAvailable(), price.getEffectivePrice(), price.getCampaignId(),
                product.getBottlePrice(), product.getBottleVolumeMl(), product.getBottleStock());
    }

    @PostMapping("/{productId}/sales")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void commitSale(@PathVariable Long productId, @RequestParam Long tenantId,
                           @RequestHeader(value = "X-Internal-Service-Key", required = false) String key,
                           @Valid @RequestBody CommitRetailSaleRequest request) {
        keys.verify(key);
        retailSales.commit(tenantId, productId, request.orderId(), request.quantity());
    }
}
