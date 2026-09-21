package dev.nidhi.paymentservice.dtos;

public record RazorpayCreateOrderResponse(
        String id,
        String entity,
        Long amount,
        Long amountPaid,
        Long amountDue,
        String currency,
        String receipt,
        String status
) {
}
