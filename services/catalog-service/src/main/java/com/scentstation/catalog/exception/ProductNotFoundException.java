package com.scentstation.catalog.exception;

public class ProductNotFoundException extends RuntimeException {

    // Tạo lỗi chứa mã của sản phẩm không tồn tại.
    public ProductNotFoundException(Long id) {
        super("Product not found with id: " + id);
    }
}
