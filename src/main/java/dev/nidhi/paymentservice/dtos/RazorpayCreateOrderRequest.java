package dev.nidhi.paymentservice.dtos;


public record RazorpayCreateOrderRequest (
        Long amount,
        String currency,
        String receipt
) {}
