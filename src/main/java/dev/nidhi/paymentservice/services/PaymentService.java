package dev.nidhi.paymentservice.services;

import dev.nidhi.paymentservice.clients.RazorpayClient;
import dev.nidhi.paymentservice.dtos.RazorpayCreateOrderRequest;
import dev.nidhi.paymentservice.dtos.RazorpayCreateOrderResponse;
import dev.nidhi.paymentservice.models.CreatePaymentRequest;
import dev.nidhi.paymentservice.models.Payment;
import dev.nidhi.paymentservice.models.PaymentStatus;
import dev.nidhi.paymentservice.repositories.PaymentRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@AllArgsConstructor
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final RazorpayClient razorpayClient;

    public Payment createPayment(CreatePaymentRequest paymentRequest){
          Payment payment = new Payment();



        String receipt = "payment-"+System.currentTimeMillis();
        RazorpayCreateOrderRequest request =
                new RazorpayCreateOrderRequest(
                        paymentRequest.amount(),
                        paymentRequest.currency(),
                        receipt);

        RazorpayCreateOrderResponse response =
                razorpayClient.createOrder(request);

        payment.setAmount(response.amount());
        payment.setCurrency(response.currency());
        payment.setCreatedAt(Instant.now());
        payment.setUpdatedAt(Instant.now());
        payment.setStatus(PaymentStatus.CREATED);
        payment.setProviderOrderId(response.id());

        return paymentRepository.save(payment);
    }
}
