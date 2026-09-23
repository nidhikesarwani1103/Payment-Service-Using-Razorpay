package dev.nidhi.paymentservice.controllers;

import dev.nidhi.paymentservice.dtos.RazorPayWebhookEntity;
import dev.nidhi.paymentservice.dtos.RazorpayWebhookPayload;
import dev.nidhi.paymentservice.services.PaymentService;
import dev.nidhi.paymentservice.services.RazorpayWebhookSignatureVerifier;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.json.JsonMapper;

import java.security.NoSuchAlgorithmException;

@RestController
@RequestMapping("/webhooks/razorpay")
@AllArgsConstructor
public class RazorpayWebhookController {

    private final RazorpayWebhookSignatureVerifier signatureVerifier;
    private final JsonMapper jsonMapper;
    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<String> handleWebhook(
                                        @RequestBody String payload,
                                        @RequestHeader("X-Razorpay-Signature") String signature)
                                        throws NoSuchAlgorithmException {
        boolean valid = signatureVerifier.verify(payload, signature);
        if(!valid){
            return ResponseEntity.badRequest().build();
        }


        try{
            RazorpayWebhookPayload webhoook =  jsonMapper.readValue(
                    payload, RazorpayWebhookPayload.class
            );

            RazorPayWebhookEntity entity = webhoook
                                    .payload().payment().entity();

            if("payment.captured".equals(webhoook.event())){
                paymentService.handlePaymentCaptured(
                        entity.orderId(), entity.id(), entity.amount());
            }
            else if ("payment.failed".equals(webhoook.event())){
                paymentService.handlePaymentFailed(
                        entity.orderId(), entity.id(), entity.amount());
            }
        }
        catch (Exception e){
            System.out.println("Failed tp parse json payload!");
        }

        System.out.println("Razorpay webhook received!");
        System.out.println(payload);
        return ResponseEntity.ok().build();
    }
}
