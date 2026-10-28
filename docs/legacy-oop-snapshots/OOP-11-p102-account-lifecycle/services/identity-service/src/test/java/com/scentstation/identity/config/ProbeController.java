package com.scentstation.identity.config;

import org.springframework.web.bind.annotation.GetMapping;
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
}