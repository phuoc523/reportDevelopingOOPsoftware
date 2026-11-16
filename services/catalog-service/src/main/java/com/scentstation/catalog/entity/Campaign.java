package com.scentstation.catalog.entity;

import com.scentstation.catalog.enums.DiscountType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "campaigns")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Campaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Xác định tenant sở hữu chương trình khuyến mãi.
    @Column(nullable = false)
    private Long tenantId;

    // Sản phẩm được áp dụng chương trình khuyến mãi.
    @Column(nullable = false)
    private Long productId;

    // Tên chương trình để quản trị viên dễ nhận biết.
    @Column(nullable = false)
    private String name;

    // Xác định chương trình giảm theo phần trăm hay số tiền cố định.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DiscountType discountType;

    // Giá trị giảm, ví dụ 20 đối với 20% hoặc 15000 đối với giảm 15.000.
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal discountValue;

    // Thời điểm chương trình bắt đầu có hiệu lực.
    @Column(nullable = false)
    private LocalDateTime startAt;

    // Thời điểm chương trình kết thúc hiệu lực.
    @Column(nullable = false)
    private LocalDateTime endAt;

    // Cho phép tạm bật hoặc tắt campaign mà không cần xóa dữ liệu.
    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;

    @Column(nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    @Setter(AccessLevel.NONE)
    private LocalDateTime updatedAt;

    // Gán thời điểm tạo và cập nhật ban đầu trước khi campaign được lưu lần đầu.
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    // Làm mới thời điểm cập nhật trước khi campaign được lưu thay đổi.
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}