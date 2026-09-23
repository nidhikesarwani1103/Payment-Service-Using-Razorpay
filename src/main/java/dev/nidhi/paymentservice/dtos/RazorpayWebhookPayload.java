package dev.nidhi.paymentservice.dtos;

public record RazorpayWebhookPayload(
        String event,
        RazorPayWebhookPaymentPayload payload
)
{
}
