package dev.nidhi.paymentservice.services;

import dev.nidhi.paymentservice.models.Payment;
import dev.nidhi.paymentservice.models.PaymentStatus;
import dev.nidhi.paymentservice.repositories.PaymentRepository;
import lombok.AllArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

@Component
@AllArgsConstructor
public class PaymentReconciliationJob {
    private final PaymentService paymentService;
    private final PaymentRepository paymentRepository;

    // There is all args constructor in annotation

    @Scheduled(fixedDelay = 5*60*1000)
    public void reconcile(){
        List<Payment> paymentList = paymentRepository.findByStatusIn(
                List.of(PaymentStatus.CREATED, PaymentStatus.PENDING));

        if(paymentList.isEmpty()){
            return;
        }

        for(Payment payment : paymentList){
           paymentService.reconcilePayment(payment);
        }
    }

}
