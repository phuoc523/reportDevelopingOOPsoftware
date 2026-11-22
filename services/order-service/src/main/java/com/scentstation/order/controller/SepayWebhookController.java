package com.scentstation.order.controller;

import com.scentstation.order.service.SepayCredit;
import com.scentstation.order.service.SepaySettings;
import com.scentstation.order.service.SepaySettlement;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Validator;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
public class SepayWebhookController {
    private static final int MAX_PAYLOAD_BYTES = 16384;
    private final SepaySettings settings;
    private final SepaySettlement settlement;

    private final ObjectReader reader;
    private final Validator validator;

    public SepayWebhookController(SepaySettings settings, SepaySettlement settlement,
                                   ObjectMapper mapper, Validator validator) {
        this.settings = settings;
        this.settlement = settlement;
        this.reader = mapper.readerFor(SepayCredit.class)
                .with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.validator = validator;
    }

    @PostMapping(path = "/api/v1/webhooks/sepay", consumes = "application/json")
    public Map<String, Boolean> receive(
            @RequestHeader(value = "X-SePay-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-SePay-Signature", required = false) String signature,
            HttpServletRequest request) throws IOException {
        if (!settings.enabled()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        byte[] body = request.getInputStream().readNBytes(MAX_PAYLOAD_BYTES + 1);
        if (body.length > MAX_PAYLOAD_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
        if (!settings.authenticates(timestamp, signature, body)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        SepayCredit credit;
        try {
            credit = reader.readValue(body);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid SePay payload");
        }
        if (credit == null || !validator.validate(credit).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid SePay payload");
        }
        try {
            settlement.accept(credit, new String(body, StandardCharsets.UTF_8));
        } catch (DuplicateKeyException exception) {
            if (!settlement.hasRecordedEvent(credit)) throw exception;
        }
        return Map.of("success", true);
    }
}
