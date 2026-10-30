package com.scentstation.identity.config;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProbeController {

    @GetMapping("/api/admin/probe")
    public String adminProbe() {
        return "admin";
    }

    @GetMapping("/api/staff/probe")
    public String staffProbe() {
        return "staff";
    }

    @GetMapping("/api/customer/probe")
    public String customerProbe() {
        return "customer";
    }
}
