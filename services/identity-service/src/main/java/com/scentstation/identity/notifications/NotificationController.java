package com.scentstation.identity.notifications;

import com.scentstation.identity.auth.AuthDto;
import com.scentstation.identity.model.StaffUser;
import com.scentstation.identity.platform.InternalServiceKeys;
import com.scentstation.identity.platform.PlatformConfigService;
import com.scentstation.identity.repository.StaffUserRepository;
import com.scentstation.identity.tenant.TenantScopeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.Clock;
import java.util.List;
import java.util.Set;

@RestController
public class NotificationController {
    private final NotificationEventRepository events;
    private final NotificationDeliveryRepository deliveries;
    private final StaffUserRepository users;
    private final TenantScopeService scopes;
    private final PlatformConfigService config;
    private final Clock clock;
    private final String secret;
    public NotificationController(NotificationEventRepository events,NotificationDeliveryRepository deliveries,
        StaffUserRepository users,TenantScopeService scopes,PlatformConfigService config,Clock clock,
        @Value("${NOTIFICATION_SERVICE_KEY:}") String secret){
        this.events=events;this.deliveries=deliveries;this.users=users;this.scopes=scopes;this.config=config;this.clock=clock;this.secret=secret;
    }
    public record SubmitRequest(@NotBlank @Size(max=64) String tenantId,
        @NotBlank @Size(max=80) String eventId,
        @NotBlank @Size(max=64) String eventType,
        @NotBlank @Size(max=120) String title,
        @NotBlank @Size(max=500) String message,
        @NotEmpty Set<@NotBlank String> recipientRoles){}
    public record Result(String id,boolean duplicate,int delivered,String status){}
    /** Demo delivery channel: a saved IN_APP inbox entry is DELIVERED; no email/SMS promises. */
    @PostMapping("/api/v1/internal/notifications/events")
    @Transactional
    public ResponseEntity<Result> submit(
        @RequestHeader(name="X-Internal-Service-Key",required=false) String supplied,
        @Valid @RequestBody SubmitRequest input){
        InternalServiceKeys.verify(secret,supplied);
        NotificationEvent prior=events.findByTenantIdAndEventId(input.tenantId(),input.eventId()).orElse(null);
        if(prior!=null)return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(new Result(prior.getId(),true,0,"DUPLICATE_IGNORED"));
        NotificationEvent e=events.save(new NotificationEvent(input.tenantId(),input.eventId(),input.eventType(),clock.instant()));
        if(!config.notificationsEnabledForTenant(input.tenantId())){
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new Result(e.getId(),false,0,"DISABLED_BY_TENANT_CONFIG"));
        }
        List<StaffUser> matching=users.findByTenant_Id(input.tenantId()).stream()
            .filter(StaffUser::isEnabled)
            .filter(u->u.getRoles().stream().anyMatch(input.recipientRoles()::contains)).toList();
        for(StaffUser u:matching){
            deliveries.save(new NotificationDelivery(input.eventId(),input.tenantId(),u.getId(),
                input.title(),input.message(),clock.instant()));
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(new Result(e.getId(),false,matching.size(),matching.isEmpty()?"NO_RECIPIENTS":"DELIVERED_IN_APP"));
    }
    @GetMapping("/api/v1/notifications/me")
    @Transactional(readOnly=true)
    public ResponseEntity<List<NotificationDelivery>> inbox(Authentication authentication){
        AuthDto.Identity actor=scopes.authenticated(authentication);
        if(actor.tenantId()==null)return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(List.of());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(deliveries.findByTenantIdAndUserIdOrderByCreatedAtDesc(actor.tenantId(),actor.userId(),PageRequest.of(0,50)));
    }
}
