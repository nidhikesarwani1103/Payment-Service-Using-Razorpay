package dev.nidhi.paymentservice.service;

import dev.nidhi.paymentservice.configs.RazorpayConfig;
import dev.nidhi.paymentservice.services.RazorpayWebhookSignatureVerifier;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RazorpayWebhookSignatureVerifierTest {

    @Test
    void shouldVerifyValidSignature() throws Exception {
        String webHookSecret = "razorpay-secret";
        RazorpayConfig razorpayConfig = new RazorpayConfig();
        razorpayConfig.setWebhookSecret(webHookSecret);

        RazorpayWebhookSignatureVerifier signatureVerifier
                = new RazorpayWebhookSignatureVerifier(razorpayConfig);

        String payload = "{\"event\":\"payment.captured\"}";
        Mac mac = Mac.getInstance("HmacSHA256");

        SecretKeySpec secretKeySpec = new SecretKeySpec(
                webHookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"
        );
        mac.init(secretKeySpec);

        byte[] hash = mac.doFinal(
                payload.getBytes(StandardCharsets.UTF_8)
        );

        StringBuilder expectedSignature = new StringBuilder();
        for(byte b: hash){
          expectedSignature.append(String.format("%02x", b));
        }

        assertTrue(
                signatureVerifier.verify(payload,
                        expectedSignature.toString())
        );
    }

    @Test
    void shouldRejectInvalidSignature() throws NoSuchAlgorithmException {

        String webHookSecret = "razorpay-secret";
        RazorpayConfig config = new RazorpayConfig();

        config.setWebhookSecret(webHookSecret);

        RazorpayWebhookSignatureVerifier verifier =
                new RazorpayWebhookSignatureVerifier(config);

        String payload = "{\"event\":\"payment.captured\"}";

        assertFalse(verifier.verify(payload, "invalid-signature"));
    }
}
