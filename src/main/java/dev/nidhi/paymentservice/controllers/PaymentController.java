package dev.nidhi.paymentservice.controllers;

import dev.nidhi.paymentservice.models.CreatePaymentRequest;
import dev.nidhi.paymentservice.models.Payment;
import dev.nidhi.paymentservice.repositories.PaymentRepository;
import dev.nidhi.paymentservice.services.PaymentService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payments")
@AllArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;
    private final PaymentRepository paymentRepository;

    @PostMapping("")
    public ResponseEntity<Payment> createPayment
            (@RequestBody @Valid CreatePaymentRequest paymentRequest) {
        Payment payment = paymentService.createPayment(paymentRequest);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(payment);
    }

    @GetMapping("/{id}/reconcile")
    public ResponseEntity<Void> reconcilePayment(@PathVariable Long id){
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found"));

        paymentService.reconcilePayment(payment);

        return ResponseEntity.ok().build();
    }
}
