package com.scentstation.catalog.repository;

import com.scentstation.catalog.entity.Campaign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CampaignRepository extends JpaRepository<Campaign, Long> {

    // Lấy toàn bộ campaign thuộc một tenant.
    List<Campaign> findAllByTenantId(Long tenantId);

    // Lấy toàn bộ campaign của một sản phẩm trong tenant.
    List<Campaign> findAllByTenantIdAndProductId(
            Long tenantId,
            Long productId
    );

    // Tìm campaign theo id và tenant để giữ tenant isolation.
    Optional<Campaign> findByIdAndTenantId(
            Long id,
            Long tenantId
    );

    // Tìm các campaign đang active và nằm trong khoảng thời gian hiệu lực.
    List<Campaign> findAllByTenantIdAndProductIdAndActiveTrueAndStartAtLessThanEqualAndEndAtGreaterThanEqual(
            Long tenantId,
            Long productId,
            LocalDateTime startAt,
            LocalDateTime endAt
    );
}