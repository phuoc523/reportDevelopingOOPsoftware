package com.scentstation.notification.dto.response;

import java.util.List;
import org.springframework.data.domain.Page;

public record NotificationPageResponse(
    List<NotificationApiResponse> items,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
    public NotificationPageResponse {
        items = List.copyOf(items);
    }

    public static NotificationPageResponse from(Page<NotificationApiResponse> page) {
        return new NotificationPageResponse(
            page.getContent(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages()
        );
    }
}
