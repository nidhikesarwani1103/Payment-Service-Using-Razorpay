package dev.nidhi.paymentservice.repositories;

import dev.nidhi.paymentservice.models.Payment;
import dev.nidhi.paymentservice.models.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByProviderOrderId(String providerOrderId);
    List<Payment> findByStatusAndExpiresAtBefore(PaymentStatus status, Instant expiresAt);
    List<Payment> findByStatusIn (List<PaymentStatus> statuses);
}
