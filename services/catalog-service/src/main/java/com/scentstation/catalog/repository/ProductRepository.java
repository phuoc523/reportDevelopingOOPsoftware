package com.scentstation.catalog.repository;

import com.scentstation.catalog.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository
        extends JpaRepository<Product, Long>,
        JpaSpecificationExecutor<Product> {

    // Lấy toàn bộ sản phẩm chỉ trong tenant đã được xác minh.
    List<Product> findAllByTenantId(Long tenantId);

    // Tìm sản phẩm theo id và tenant để che resource của tenant khác như không tồn tại.
    Optional<Product> findByIdAndTenantId(Long id, Long tenantId);
}
