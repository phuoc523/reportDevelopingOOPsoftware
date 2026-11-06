package com.scentstation.identity.platform;

import com.scentstation.identity.tenant.TenantScopeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/platform-config")
public class PlatformConfigController {
    private final PlatformConfigService config;
    private final TenantScopeService scopes;
    public PlatformConfigController(PlatformConfigService config,TenantScopeService scopes){this.config=config;this.scopes=scopes;}
    public record ValueRequest(@NotBlank @Size(max=100) String value){}
    @GetMapping("/global/{key}")
    public ResponseEntity<PlatformConfigService.ConfigView> getGlobal(@PathVariable String key,Authentication auth){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(config.getGlobal(scopes.authenticated(auth),key));
    }
    @PutMapping("/global/{key}")
    public ResponseEntity<PlatformConfigService.ConfigView> putGlobal(@PathVariable String key,@Valid @RequestBody ValueRequest input,Authentication auth){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(config.putGlobal(scopes.authenticated(auth),key,input.value()));
    }
    @GetMapping("/tenants/{tenantId}/{key}")
    public ResponseEntity<PlatformConfigService.ConfigView> getTenant(@PathVariable String tenantId,@PathVariable String key,Authentication auth){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(config.getTenant(scopes.authenticated(auth),tenantId,key));
    }
    @PutMapping("/tenants/{tenantId}/{key}")
    public ResponseEntity<PlatformConfigService.ConfigView> putTenant(@PathVariable String tenantId,@PathVariable String key,@Valid @RequestBody ValueRequest input,Authentication auth){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(config.putTenant(scopes.authenticated(auth),tenantId,key,input.value()));
    }
}
