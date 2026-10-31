package com.scentstation.catalog.service;

import com.scentstation.catalog.dto.response.ProductMachineAvailabilityResponse;
import com.scentstation.catalog.entity.Product;
import com.scentstation.catalog.exception.MachineAvailabilityReferenceMismatchException;
import com.scentstation.catalog.exception.ProductNotFoundException;
import com.scentstation.catalog.integration.machine.client.MachineAvailabilityClient;
import com.scentstation.catalog.integration.machine.dto.MachineAvailabilityInfo;
import com.scentstation.catalog.repository.ProductRepository;
import com.scentstation.catalog.security.CurrentActorContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductMachineAvailabilityService {

    private final ProductRepository productRepository;
    private final MachineAvailabilityClient machineAvailabilityClient;
    private final CurrentActorContext currentActorContext;

    // Kết hợp availability chỉ khi product thuộc trusted tenant.
    @Transactional(readOnly = true)
    public ProductMachineAvailabilityResponse getAvailability(
            Long tenantId,
            Long machineId,
            Long productId
    ) {
        Long verifiedTenantId = currentActorContext.requireTenant(tenantId);
        Product product = productRepository
                .findByIdAndTenantId(productId, verifiedTenantId)
                .orElseThrow(() ->
                        new ProductNotFoundException(productId)
                );

        MachineAvailabilityInfo machineAvailability =
                machineAvailabilityClient
                        .getProductAvailability(
                                verifiedTenantId,
                                machineId,
                                productId
                        );

        validateMachineReference(
                machineAvailability,
                verifiedTenantId,
                machineId,
                productId
        );

        boolean catalogAvailable =
                Boolean.TRUE.equals(
                        product.getAvailable()
                );

        boolean machineAvailable =
                machineAvailability.isAvailable();

        boolean available =
                catalogAvailable
                        && machineAvailable;

        return ProductMachineAvailabilityResponse.builder()
                .tenantId(verifiedTenantId)
                .productId(productId)
                .machineId(machineId)
                .catalogAvailable(catalogAvailable)
                .machineAvailable(machineAvailable)
                .availableSlotCount(
                        machineAvailability
                                .getAvailableSlotCount()
                )
                .available(available)
                .build();
    }

    // Bảo đảm response từ Machine Service đúng resource và có dữ liệu availability hợp lệ.
    private void validateMachineReference(
            MachineAvailabilityInfo info,
            Long tenantId,
            Long machineId,
            Long productId
    ) {
        if (info == null
                || !tenantId.equals(info.getTenantId())
                || !machineId.equals(info.getMachineId())
                || !productId.equals(info.getProductId())
                || info.getAvailableSlotCount() == null
                || info.getAvailableSlotCount() < 0) {

            throw new MachineAvailabilityReferenceMismatchException();
        }
    }
}
