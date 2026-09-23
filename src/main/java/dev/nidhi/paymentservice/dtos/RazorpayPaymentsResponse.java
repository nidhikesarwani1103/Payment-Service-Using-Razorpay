package dev.nidhi.paymentservice.dtos;

import java.util.List;

public record RazorpayPaymentsResponse(
        String entity,
        Integer count,
        List<RazorpayPaymentResponse> items
) {
}
