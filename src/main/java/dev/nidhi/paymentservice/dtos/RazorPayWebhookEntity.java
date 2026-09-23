package dev.nidhi.paymentservice.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RazorPayWebhookEntity(
        String id,
        Long amount,
        String currency,
        String status,
        @JsonProperty("order_id")
        String orderId
) {
}
