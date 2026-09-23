package dev.nidhi.paymentservice.services;

import dev.nidhi.paymentservice.configs.RazorpayConfig;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
@AllArgsConstructor
public class RazorpayWebhookSignatureVerifier {
    private final RazorpayConfig razorpayConfig;

    public boolean verify(String payload, String signature)
                          throws NoSuchAlgorithmException {
        try{
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                            razorpayConfig.getWebhookSecret().getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256");

            mac.init(secretKeySpec);

            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));

            String expectedSignature = bytesToHex(hash);

            return MessageDigest.isEqual(
                    signature.getBytes(StandardCharsets.UTF_8),
                    expectedSignature.getBytes(StandardCharsets.UTF_8));
        }
        catch (Exception e){
            throw new IllegalStateException("Failed to verify Razorpay " +
                    "webhook signature!");
        }
    }

    private String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder();
        for(byte b : hash){
            hexString.append(String.format("%02x", b));
        }
        return hexString.toString();
    }
}
