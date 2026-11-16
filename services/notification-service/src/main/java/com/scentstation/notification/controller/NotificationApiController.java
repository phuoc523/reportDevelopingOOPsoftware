package com.scentstation.notification.controller;

import com.scentstation.notification.dto.command.CreateNotificationCommand;
import com.scentstation.notification.dto.request.CreateNotificationRequest;
import com.scentstation.notification.dto.request.MarkNotificationsReadRequest;
import com.scentstation.notification.dto.response.NotificationApiResponse;
import com.scentstation.notification.dto.response.NotificationPageResponse;
import com.scentstation.notification.dto.response.NotificationReadCountResponse;
import com.scentstation.notification.dto.response.NotificationsMarkedReadResponse;
import com.scentstation.notification.security.IdentityAuthorizationClient;
import com.scentstation.notification.security.NotificationAuthorizationException;
import com.scentstation.notification.security.NotificationAuthorizationResponse;
import com.scentstation.notification.service.NotificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@Validated
public class NotificationApiController {
    private static final String READ_PERMISSION = "NOTIFICATION_READ";
    private static final String CONFIGURE_PERMISSION = "NOTIFICATION_CONFIGURE";

    private final NotificationService notifications;
    private final IdentityAuthorizationClient identity;

    public NotificationApiController(
            NotificationService notifications,
            IdentityAuthorizationClient identity
    ) {
        this.notifications = notifications;
        this.identity = identity;
    }

    @PostMapping
    public ResponseEntity<NotificationApiResponse> create(
            @Valid @RequestBody CreateNotificationRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Service-Principal-Id", required = false) String serviceId,
            @RequestHeader(value = "X-Service-Principal-Credential", required = false) String serviceCredential
    ) {
        NotificationAuthorizationResponse caller = identity.authorize(
            authorization,
            serviceId,
            serviceCredential,
            CONFIGURE_PERMISSION,
            request.tenantId(),
            request.recipientIds()
        );
        var created = notifications.create(new CreateNotificationCommand(
            caller.tenantId(),
            request.title(),
            request.content(),
            request.notificationType(),
            request.priority(),
            request.source(),
            request.sourceEventId(),
            caller.validatedRecipientIds()
        ));
        return ResponseEntity.status(HttpStatus.CREATED).body(NotificationApiResponse.from(created));
    }

    @GetMapping("/{id}")
    public NotificationApiResponse get(
            @PathVariable @Positive Long id,
            @RequestParam(required = false) @Size(max = 100) String tenantId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Service-Principal-Id", required = false) String serviceId,
            @RequestHeader(value = "X-Service-Principal-Credential", required = false) String serviceCredential
    ) {
        NotificationAuthorizationResponse caller = identity.authorize(
            authorization, serviceId, serviceCredential, READ_PERMISSION, tenantId, List.of()
        );
        var notification = caller.isUser()
            ? notifications.getForRecipient(id, caller.tenantId(), caller.userId().toString())
            : notifications.getById(id, caller.tenantId());
        return caller.isUser()
            ? NotificationApiResponse.from(notification, caller.userId().toString())
            : NotificationApiResponse.from(notification);
    }

    @GetMapping
    public NotificationPageResponse list(
            @RequestParam(required = false) @Size(max = 100) String tenantId,
            @RequestParam(required = false) @Size(max = 50) String notificationType,
            @RequestParam(required = false) @Size(max = 20) String priority,
            @RequestParam(defaultValue = "0") @Min(0) @Max(100000) Integer page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer size,
            @RequestParam(defaultValue = "DESC")
            @Pattern(regexp = "ASC|DESC", flags = Pattern.Flag.CASE_INSENSITIVE) String sortDirection,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Service-Principal-Id", required = false) String serviceId,
            @RequestHeader(value = "X-Service-Principal-Credential", required = false) String serviceCredential
    ) {
        NotificationAuthorizationResponse caller = identity.authorize(
            authorization, serviceId, serviceCredential, READ_PERMISSION, tenantId, List.of()
        );
        Sort.Direction direction = Sort.Direction.fromString(sortDirection);
        PageRequest tenantPageable = PageRequest.of(
            page,
            size,
            Sort.by(direction, "createdAt").and(Sort.by(direction, "id"))
        );
        PageRequest recipientPageable = PageRequest.of(
            page,
            size,
            Sort.by(direction, "notification.createdAt").and(Sort.by(direction, "notification.id"))
        );
        Page<com.scentstation.notification.dto.response.NotificationResponse> results = caller.isUser()
            ? notifications.listForRecipient(
                caller.tenantId(),
                caller.userId().toString(),
                notificationType,
                priority,
                recipientPageable
            )
            : notifications.listByTenant(caller.tenantId(), notificationType, priority, tenantPageable);
        String recipientId = caller.isUser() ? caller.userId().toString() : null;
        return NotificationPageResponse.from(
            results.map(result -> NotificationApiResponse.from(result, recipientId))
        );
    }

    @PatchMapping("/{id}/read")
    public NotificationApiResponse markAsRead(
            @PathVariable @Positive Long id,
            @RequestParam(required = false) @Size(max = 100) String tenantId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Service-Principal-Id", required = false) String serviceId,
            @RequestHeader(value = "X-Service-Principal-Credential", required = false) String serviceCredential
    ) {
        NotificationAuthorizationResponse caller = identity.authorize(
            authorization, serviceId, serviceCredential, READ_PERMISSION, tenantId, List.of()
        );
        String recipientId = requireUserId(caller);
        var notification = notifications.markAsReadForRecipient(id, caller.tenantId(), recipientId);
        return NotificationApiResponse.from(notification, recipientId);
    }

    @PatchMapping("/read")
    public NotificationsMarkedReadResponse markManyAsRead(
            @Valid @RequestBody MarkNotificationsReadRequest request,
            @RequestParam(required = false) @Size(max = 100) String tenantId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Service-Principal-Id", required = false) String serviceId,
            @RequestHeader(value = "X-Service-Principal-Credential", required = false) String serviceCredential
    ) {
        NotificationAuthorizationResponse caller = identity.authorize(
            authorization, serviceId, serviceCredential, READ_PERMISSION, tenantId, List.of()
        );
        String recipientId = requireUserId(caller);
        int updatedCount = notifications.markManyAsReadForRecipient(
            caller.tenantId(),
            recipientId,
            request.notificationIds()
        );
        return new NotificationsMarkedReadResponse(updatedCount);
    }

    @GetMapping("/unread-count")
    public NotificationReadCountResponse unreadCount(
            @RequestParam(required = false) @Size(max = 100) String tenantId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Service-Principal-Id", required = false) String serviceId,
            @RequestHeader(value = "X-Service-Principal-Credential", required = false) String serviceCredential
    ) {
        NotificationAuthorizationResponse caller = identity.authorize(
            authorization, serviceId, serviceCredential, READ_PERMISSION, tenantId, List.of()
        );
        String recipientId = requireUserId(caller);
        return new NotificationReadCountResponse(
            notifications.countUnreadForRecipient(caller.tenantId(), recipientId)
        );
    }

    private String requireUserId(NotificationAuthorizationResponse caller) {
        if (!caller.isUser()) {
            throw new NotificationAuthorizationException(HttpStatus.FORBIDDEN);
        }
        return caller.userId().toString();
    }
}
