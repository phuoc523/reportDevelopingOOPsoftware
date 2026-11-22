package com.scentstation.order.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class SepayReconciliationClient {
    private static final DateTimeFormatter API_TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss");
    private final RestClient client;
    private final String token;
    private final String bankAccountId;

    public SepayReconciliationClient(RestClient.Builder builder,
                                     @Value("${payment.sepay.reconciliation.api-token:}") String token,
                                     @Value("${payment.sepay.reconciliation.bank-account-id:}") String bankAccountId) {
        this.client = builder.baseUrl("https://userapi.sepay.vn").build();
        this.token = token;
        this.bankAccountId = bankAccountId;
    }

    public Page fetch(LocalDateTime from, LocalDateTime to, int page) {
        if (token.isBlank() || bankAccountId.isBlank()) {
            throw new IllegalStateException("SePay reconciliation requires API token and bank account UUID");
        }
        return client.get()
                .uri(uri -> uri.path("/v2/transactions")
                        .queryParam("transaction_date_from", API_TIME.format(from))
                        .queryParam("transaction_date_to", API_TIME.format(to))
                        .queryParam("bank_account_id", bankAccountId)
                        .queryParam("transaction_date_sort", "asc")
                        .queryParam("per_page", 100)
                        .queryParam("page", page)
                        .build())
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(Page.class);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Page(List<Transaction> data, Meta meta) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Meta(Pagination pagination) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Pagination(int last_page, boolean has_more) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Transaction(String id, String transaction_date, String account_number,
                              String transfer_type, BigDecimal amount_in, String transaction_content,
                              String reference_number, String code, String bank_brand_name) {
        public SepayCredit toCredit() {
            return new SepayCredit(id, bank_brand_name, account_number, code, transaction_content,
                    transfer_type, amount_in, reference_number, transaction_date);
        }
    }
}
