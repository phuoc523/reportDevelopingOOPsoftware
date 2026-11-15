package com.scentstation.identity.config;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProbeController {

    @GetMapping("/api/admin/probe")
    public String admin() {
        return "admin";
    }

    @GetMapping("/api/staff/probe")
    public String staff() {
        return "staff";
    }

    @GetMapping("/api/customer/probe")
    public String customer() {
        return "customer";
    }

    @GetMapping("/api/admin/users/probe")
    public String adminUsers() {
        return "admin-users";
    }

    @GetMapping("/api/admin/staff-users/probe")
    public String adminStaffUsers() {
        return "admin-staff-users";
    }

    @GetMapping("/api/staff/operations/probe")
    public String operations() {
        return "operations";
    }

    @PostMapping("/api/staff/operations/probe")
    public String updateOperations() {
        return "operations-updated";
    }

    @GetMapping("/api/staff/inventory/probe")
    public String inventory() {
        return "inventory";
    }

    @PostMapping("/api/staff/inventory/probe")
    public String updateInventory() {
        return "inventory-updated";
    }

    @PostMapping("/api/staff/maintenance/probe")
    public String updateMaintenance() {
        return "maintenance-updated";
    }
}