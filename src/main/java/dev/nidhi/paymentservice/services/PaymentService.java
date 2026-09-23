package dev.nidhi.paymentservice.services;

import dev.nidhi.paymentservice.clients.RazorpayClient;
import dev.nidhi.paymentservice.dtos.RazorpayCreateOrderRequest;
import dev.nidhi.paymentservice.dtos.RazorpayCreateOrderResponse;
import dev.nidhi.paymentservice.dtos.RazorpayPaymentResponse;
import dev.nidhi.paymentservice.dtos.RazorpayPaymentsResponse;
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
        payment.setExpiresAt(Instant.now().plusSeconds(60*15));
        payment.setProviderOrderId(response.id());

        return paymentRepository.save(payment);
    }

    public void handlePaymentCaptured(String providerOrderId,
                                      String providerPaymentId,
                                      Long amount){
        Payment payment = paymentRepository.findByProviderOrderId(providerOrderId)
                .orElseThrow(
                        () -> new IllegalArgumentException
                                ("Payment not found with provider order id:" +
                                        " " + providerOrderId)
                );

        if(!payment.getAmount().equals(amount)){
            throw new IllegalArgumentException("Payment amount does not match with order!");
        }

        // To make the method idempotent
        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            return;
        }

        payment.setProviderPaymentId(providerPaymentId);
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setUpdatedAt(Instant.now());

        paymentRepository.save(payment);
    }

    public void handlePaymentFailed(String providerOrderId,
                                    String providerPaymentId,
                                    Long amount){
        Payment payment = paymentRepository.findByProviderOrderId(providerOrderId)
                .orElseThrow(()->
                        new IllegalArgumentException("Payment with order id: " +
                                providerOrderId + " not found"));

        if(!payment.getAmount().equals(amount)){
            throw new IllegalArgumentException
                    ("Payment amount does not match with order!");
        }

        // we should not change the success to failed
        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            return;
        }
        if (payment.getStatus() == PaymentStatus.FAILED) {
            return;
        }

        payment.setProviderPaymentId(providerPaymentId);
        payment.setStatus(PaymentStatus.FAILED);
        payment.setUpdatedAt(Instant.now());

        paymentRepository.save(payment);
    }

    public void reconcilePayment(Payment payment){
        RazorpayPaymentsResponse response =
                razorpayClient.getPaymentsForOrder(payment.getProviderOrderId());

        // No payment attempted for this order
        if(response.items()==null || response.items().isEmpty()){
            return;
        }

        RazorpayPaymentResponse paymentResponse = response.items().getFirst();

        if(!paymentResponse.amount().equals(payment.getAmount())){
            throw new IllegalArgumentException
                    ("Payment amount does not match with order!");
        }

        System.out.println("Payment response item: "+paymentResponse);
        switch(paymentResponse.status()){

            case "captured" -> {
               payment.setProviderPaymentId(paymentResponse.id());
               payment.setStatus(PaymentStatus.SUCCESS);

            }

            case "failed" ->{
                payment.setProviderPaymentId(paymentResponse.id());
                payment.setStatus(PaymentStatus.FAILED);
            }

            default -> {
                return;
            }
        }


        payment.setUpdatedAt(Instant.now());
        paymentRepository.save(payment);
    }
}
