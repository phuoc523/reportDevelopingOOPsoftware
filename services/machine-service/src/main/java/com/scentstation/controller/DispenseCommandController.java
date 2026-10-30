package com.scentstation.controller;

import com.scentstation.dto.request.CreateDispenseCommandRequest;
import com.scentstation.dto.response.DispenseCommandResponse;
import com.scentstation.service.DispenseCommandService;
import com.scentstation.service.InternalServiceKeyVerifier;
import org.springframework.web.bind.annotation.RequestHeader;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/machines/dispense-commands")
public class DispenseCommandController {
    private final DispenseCommandService service;
    private final InternalServiceKeyVerifier keyVerifier;

    public DispenseCommandController(DispenseCommandService service,
                                     InternalServiceKeyVerifier keyVerifier) {
        this.service = service;
        this.keyVerifier = keyVerifier;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DispenseCommandResponse create(@RequestHeader(value = "X-Internal-Service-Key", required = false) String serviceKey,
                                          @Valid @RequestBody CreateDispenseCommandRequest request) {
        keyVerifier.verify(serviceKey);
        return service.create(request);
    }

    @GetMapping("/{commandId}")
    public DispenseCommandResponse get(@PathVariable String commandId,
                                       @RequestParam(value = "tenantId", required = false) String tenantId,
                                       @RequestHeader(value = "X-Internal-Service-Key", required = false) String serviceKey) {
        keyVerifier.verify(serviceKey);
        return service.get(commandId, tenantId);
    }
}
