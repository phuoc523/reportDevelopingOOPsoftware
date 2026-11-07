package com.scentstation.catalog.repository;

import com.scentstation.catalog.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findAllByTenantId(Long tenantId);

    Optional<Product> findByIdAndTenantId(Long id, Long tenantId);
}
