package com.scentstation.catalog.service;

import com.scentstation.catalog.dto.response.ProductMachineAvailabilityResponse;
import com.scentstation.catalog.entity.Product;
import com.scentstation.catalog.exception.MachineAvailabilityReferenceMismatchException;
import com.scentstation.catalog.exception.ProductNotFoundException;
import com.scentstation.catalog.integration.machine.client.MachineAvailabilityClient;
import com.scentstation.catalog.integration.machine.dto.MachineAvailabilityInfo;
import com.scentstation.catalog.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductMachineAvailabilityService {

    private final ProductRepository productRepository;
    private final MachineAvailabilityClient machineAvailabilityClient;

    // Kết hợp trạng thái Catalog và Machine để xác định product có thể phục vụ hay không.
    @Transactional(readOnly = true)
    public ProductMachineAvailabilityResponse getAvailability(
            Long tenantId,
            Long machineId,
            Long productId
    ) {
        Product product = productRepository
                .findById(productId)
                .orElseThrow(() ->
                        new ProductNotFoundException(productId)
                );

        // Không cho tenant khác truy cập product không thuộc tenant yêu cầu.
        if (!product.getTenantId().equals(tenantId)) {
            throw new ProductNotFoundException(productId);
        }

        MachineAvailabilityInfo machineAvailability =
                machineAvailabilityClient
                        .getProductAvailability(
                                tenantId,
                                machineId,
                                productId
                        );

        validateMachineReference(
                machineAvailability,
                tenantId,
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
                .tenantId(tenantId)
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