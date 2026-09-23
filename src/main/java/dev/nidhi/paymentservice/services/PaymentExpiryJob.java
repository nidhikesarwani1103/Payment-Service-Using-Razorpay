package dev.nidhi.paymentservice.services;

import dev.nidhi.paymentservice.models.Payment;
import dev.nidhi.paymentservice.models.PaymentStatus;
import dev.nidhi.paymentservice.repositories.PaymentRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class PaymentExpiryJob {
    private final PaymentRepository paymentRepository;

    public PaymentExpiryJob(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Scheduled(fixedDelay = 60_000)
    public void expirePayments(){
        Instant now = Instant.now();

        List<Payment> payments = paymentRepository.findByStatusAndExpiresAtBefore(
                PaymentStatus.CREATED, now);

        for(Payment payment: payments){
            payment.setStatus(PaymentStatus.EXPIRED);
            payment.setUpdatedAt(now);

            paymentRepository.save(payment);
        }
    }
}
