package com.scentstation.catalog.specification;

import com.scentstation.catalog.dto.request.ProductSearchCriteria;
import com.scentstation.catalog.entity.Product;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;

public final class ProductSpecification {

    private ProductSpecification() {
    }

    // Xây dựng specification động từ các điều kiện tìm kiếm được cung cấp.
    public static Specification<Product> from(
            ProductSearchCriteria criteria
    ) {
        Specification<Product> specification =
                Specification.where(null);

        if (criteria.getTenantId() != null) {
            specification = specification.and(
                    hasTenant(criteria.getTenantId())
            );
        }

        if (criteria.getKeyword() != null
                && !criteria.getKeyword().isBlank()) {

            specification = specification.and(
                    containsKeyword(criteria.getKeyword())
            );
        }

        if (criteria.getAvailable() != null) {
            specification = specification.and(
                    hasAvailability(
                            criteria.getAvailable()
                    )
            );
        }

        if (criteria.getMinPrice() != null) {
            specification = specification.and(
                    priceGreaterThanOrEqual(
                            criteria.getMinPrice()
                    )
            );
        }

        if (criteria.getMaxPrice() != null) {
            specification = specification.and(
                    priceLessThanOrEqual(
                            criteria.getMaxPrice()
                    )
            );
        }

        return specification;
    }

    // Lọc sản phẩm thuộc một tenant cụ thể.
    private static Specification<Product> hasTenant(
            Long tenantId
    ) {
        return (root, query, builder) ->
                builder.equal(
                        root.get("tenantId"),
                        tenantId
                );
    }

    // Tìm keyword không phân biệt hoa thường trong các trường mô tả sản phẩm.
    private static Specification<Product> containsKeyword(
            String keyword
    ) {
        String pattern =
                "%"
                        + keyword.toLowerCase().trim()
                        + "%";

        return (root, query, builder) ->
                builder.or(
                        builder.like(
                                builder.lower(
                                        root.get("name")
                                ),
                                pattern
                        ),
                        builder.like(
                                builder.lower(
                                        root.get("description")
                                ),
                                pattern
                        ),
                        builder.like(
                                builder.lower(
                                        root.get("fragranceNotes")
                                ),
                                pattern
                        )
                );
    }

    // Lọc sản phẩm theo trạng thái khả dụng.
    private static Specification<Product> hasAvailability(
            Boolean available
    ) {
        return (root, query, builder) ->
                builder.equal(
                        root.get("available"),
                        available
                );
    }

    // Lọc sản phẩm có giá mặc định lớn hơn hoặc bằng mức tối thiểu.
    private static Specification<Product>
    priceGreaterThanOrEqual(
            BigDecimal minPrice
    ) {
        return (root, query, builder) ->
                builder.greaterThanOrEqualTo(
                        root.get("defaultPrice"),
                        minPrice
                );
    }

    // Lọc sản phẩm có giá mặc định nhỏ hơn hoặc bằng mức tối đa.
    private static Specification<Product>
    priceLessThanOrEqual(
            BigDecimal maxPrice
    ) {
        return (root, query, builder) ->
                builder.lessThanOrEqualTo(
                        root.get("defaultPrice"),
                        maxPrice
                );
    }
}