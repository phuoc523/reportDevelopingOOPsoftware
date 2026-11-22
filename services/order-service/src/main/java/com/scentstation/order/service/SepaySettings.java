package com.scentstation.order.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.regex.Pattern;

@Component
public class SepaySettings {
    private static final Pattern REFERENCE = Pattern.compile("SS[0-9A-F]{20}");
    private final boolean enabled;
    private final String account;
    private final String holder;
    private final String key;

    public SepaySettings(@Value("${payment.provider:MOCK}") String provider,
                        @Value("${payment.sepay.account:}") String account,
                        @Value("${payment.sepay.holder:}") String holder,
                        @Value("${payment.sepay.webhook-secret:}") String key) {
        if (!provider.equals("MOCK") && !provider.equals("SEPAY")) {
            throw new IllegalArgumentException("PAYMENT_PROVIDER must be MOCK or SEPAY");
        }
        enabled = provider.equals("SEPAY");
        if (enabled && (!account.matches("[0-9]{6,20}") || holder.isBlank()
                || key.length() < 32 || !key.matches("[!-~]+"))) {
            throw new IllegalArgumentException("SEPAY requires account, holder and a webhook secret of at least 32 characters");
        }
        this.account = account;
        this.holder = holder;
        this.key = key;
    }

    public boolean enabled() { return enabled; }
    public String holder() { return holder; }
    public String account() { return account; }

    public static boolean isSepayReference(String reference) {
        return REFERENCE.matcher(reference).matches();
    }

    public boolean authenticates(String timestamp, String signature, byte[] rawBody) {
        if (!enabled || timestamp == null || !timestamp.matches("[0-9]{10,11}")
                || signature == null || !signature.matches("sha256=[0-9a-f]{64}")) return false;
        long now = Instant.now().getEpochSecond();
        long signedAt = Long.parseLong(timestamp);
        if (signedAt < now - 300 || signedAt > now + 300) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update((timestamp + ".").getBytes(StandardCharsets.US_ASCII));
            return MessageDigest.isEqual(mac.doFinal(rawBody), HexFormat.of().parseHex(signature.substring(7)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Cannot verify SePay webhook signature", exception);
        }
    }

    public String qrImage(String reference, BigDecimal amount) {
        long vnd = amount.longValueExact();
        if (vnd <= 0 || !isSepayReference(reference)) {
            throw new IllegalArgumentException("Invalid VietQR transfer");
        }
        return UriComponentsBuilder.fromUriString("https://vietqr.app/img")
                .queryParam("bank", "MB")
                .queryParam("acc", account)
                .queryParam("amount", vnd)
                .queryParam("des", reference)
                .queryParam("template", "qronly")
                .build().toUriString();
    }
}
