package dev.nidhi.paymentservice.models;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreatePaymentRequest(
        @NotNull
        @Positive
        Long amount,
        @NotBlank
        String currency
) { }
