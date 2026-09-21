package dev.nidhi.paymentservice.controllers;

import dev.nidhi.paymentservice.models.CreatePaymentRequest;
import dev.nidhi.paymentservice.models.Payment;
import dev.nidhi.paymentservice.services.PaymentService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payments")
@AllArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;

    @PostMapping("")
    public ResponseEntity<Payment> createPayment
            (@RequestBody @Valid CreatePaymentRequest paymentRequest) {
        Payment payment = paymentService.createPayment(paymentRequest);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(payment);
    }
}
