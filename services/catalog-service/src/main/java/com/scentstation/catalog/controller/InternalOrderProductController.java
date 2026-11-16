package com.scentstation.catalog.controller;

import com.scentstation.catalog.dto.response.EffectivePriceResponse;
import com.scentstation.catalog.dto.response.OrderProductSnapshotResponse;
import com.scentstation.catalog.entity.Product;
import com.scentstation.catalog.exception.ProductNotFoundException;
import com.scentstation.catalog.repository.ProductRepository;
import com.scentstation.catalog.service.InternalServiceKeyVerifier;
import com.scentstation.catalog.service.PromotionalPricingService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/catalog/order-products")
public class InternalOrderProductController {
    private final ProductRepository products;
    private final PromotionalPricingService pricing;
    private final InternalServiceKeyVerifier keys;

    public InternalOrderProductController(ProductRepository products, PromotionalPricingService pricing,
                                          InternalServiceKeyVerifier keys) {
        this.products = products;
        this.pricing = pricing;
        this.keys = keys;
    }

    @GetMapping("/{productId}")
    public OrderProductSnapshotResponse get(@PathVariable Long productId, @RequestParam Long tenantId,
                                            @RequestHeader(value = "X-Internal-Service-Key", required = false) String key) {
        keys.verify(key);
        Product product = products.findByIdAndTenantId(productId, tenantId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        EffectivePriceResponse price = pricing.getInternalEffectivePrice(product);
        return new OrderProductSnapshotResponse(product.getId(), product.getTenantId(), product.getName(),
                product.getAvailable(), price.getEffectivePrice(), price.getCampaignId());
    }
}
