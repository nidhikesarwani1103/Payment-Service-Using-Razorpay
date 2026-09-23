package dev.nidhi.paymentservice.configs;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "razorpay")
public class RazorpayConfig {
    private String baseUrl;
    private String keyId;
    private String keySecret;
    private String webhookSecret;
}
