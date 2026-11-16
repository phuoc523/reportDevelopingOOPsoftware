package com.scentstation.identity.account;

import com.scentstation.identity.account.AccountRequests.*;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AdminUserController {
    private final AccountAdminService service;
    public AdminUserController(AccountAdminService service) { this.service = service; }

    @PostMapping("/platform/users") @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@accessPolicy.platform()")
    public AccountResponse createPlatform(@Valid @RequestBody Create r) { return service.create(r, true); }

    @PostMapping("/brand/users") @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@accessPolicy.has('BRAND_USER_MANAGE')")
    public AccountResponse createBrand(@Valid @RequestBody Create r) { return service.create(r, false); }

    @GetMapping("/platform/users")
    @PreAuthorize("@accessPolicy.platform()")
    public List<AccountResponse> listPlatform(@RequestParam(required=false) Long tenantId) { return service.list(true, tenantId); }

    @GetMapping("/brand/users")
    @PreAuthorize("@accessPolicy.has('BRAND_USER_MANAGE')")
    public List<AccountResponse> listBrand() { return service.list(false, null); }

    @GetMapping("/platform/users/{id}")
    @PreAuthorize("@accessPolicy.platform()")
    public AccountResponse detailPlatform(@PathVariable Long id) { return service.detail(id, true); }

    @GetMapping("/brand/users/{id}")
    @PreAuthorize("@accessPolicy.has('BRAND_USER_MANAGE')")
    public AccountResponse detailBrand(@PathVariable Long id) { return service.detail(id, false); }

    @PatchMapping("/platform/users/{id}")
    @PreAuthorize("@accessPolicy.platform()")
    public AccountResponse updatePlatform(@PathVariable Long id, @Valid @RequestBody Profile r) {
        return service.updateProfile(id, r, true);
    }

    @PatchMapping("/brand/users/{id}")
    @PreAuthorize("@accessPolicy.has('BRAND_USER_MANAGE')")
    public AccountResponse updateBrand(@PathVariable Long id, @Valid @RequestBody Profile r) {
        return service.updateProfile(id, r, false);
    }

    @PatchMapping("/platform/users/{id}/roles")
    @PreAuthorize("@accessPolicy.platform()")
    public AccountResponse rolesPlatform(@PathVariable Long id, @Valid @RequestBody Roles r) { return service.roles(id, r, true); }

    @PatchMapping("/brand/users/{id}/roles")
    @PreAuthorize("@accessPolicy.has('BRAND_USER_MANAGE')")
    public AccountResponse rolesBrand(@PathVariable Long id, @Valid @RequestBody Roles r) { return service.roles(id, r, false); }

    @PatchMapping("/platform/users/{id}/grants")
    @PreAuthorize("@accessPolicy.platform()")
    public AccountResponse grantsPlatform(@PathVariable Long id, @Valid @RequestBody Grants r) { return service.grants(id, r, true); }

    @PatchMapping("/brand/users/{id}/grants")
    @PreAuthorize("@accessPolicy.has('BRAND_USER_MANAGE')")
    public AccountResponse grantsBrand(@PathVariable Long id, @Valid @RequestBody Grants r) { return service.grants(id, r, false); }

    @PatchMapping("/platform/users/{id}/status")
    @PreAuthorize("@accessPolicy.platform()")
    public AccountResponse statusPlatform(@PathVariable Long id, @Valid @RequestBody Status r) { return service.status(id, r, true); }

    @PatchMapping("/brand/users/{id}/status")
    @PreAuthorize("@accessPolicy.has('BRAND_USER_MANAGE')")
    public AccountResponse statusBrand(@PathVariable Long id, @Valid @RequestBody Status r) { return service.status(id, r, false); }

    @PatchMapping("/platform/users/{id}/scopes")
    @PreAuthorize("@accessPolicy.platform()")
    public AccountResponse scopesPlatform(@PathVariable Long id, @Valid @RequestBody Scopes r) { return service.scopes(id, r, true); }

    @PatchMapping("/brand/users/{id}/scopes")
    @PreAuthorize("@accessPolicy.has('BRAND_USER_MANAGE')")
    public AccountResponse scopesBrand(@PathVariable Long id, @Valid @RequestBody Scopes r) { return service.scopes(id, r, false); }

    @PostMapping("/platform/users/{id}/reset-password") @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@accessPolicy.platform()")
    public void resetPlatform(@PathVariable Long id, @Valid @RequestBody Password r) { service.resetPassword(id, r, true); }

    @PostMapping("/brand/users/{id}/reset-password") @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@accessPolicy.has('BRAND_USER_MANAGE')")
    public void resetBrand(@PathVariable Long id, @Valid @RequestBody Password r) { service.resetPassword(id, r, false); }
}
