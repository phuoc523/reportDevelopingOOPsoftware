package com.scentstation.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangeUsernameRequest(
    @NotBlank(message = "Tên đăng nhập mới không được để trống")
    @Size(min = 3, max = 80, message = "Tên đăng nhập mới phải từ 3 đến 80 ký tự")
    String newUsername
) {}
