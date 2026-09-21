package dev.nidhi.paymentservice.clients;

import dev.nidhi.paymentservice.configs.RazorpayConfig;
import dev.nidhi.paymentservice.dtos.RazorpayCreateOrderRequest;
import dev.nidhi.paymentservice.dtos.RazorpayCreateOrderResponse;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class RazorpayClient {
    private final RestClient restClient;
    private final RazorpayConfig razorpayConfig;

    public RazorpayClient(RestClient.Builder restClientBuilder,
                          RazorpayConfig razorpayConfig) {

        this.razorpayConfig = razorpayConfig;
        this.restClient = restClientBuilder
                .baseUrl(razorpayConfig.getBaseUrl())
                .build();
    }


    public RazorpayCreateOrderResponse createOrder
            (RazorpayCreateOrderRequest razorpayCreateOrderRequest) {
        return restClient
                .post()
                .uri("/v1/orders")
                .headers(headers -> headers.setBasicAuth(
                                                        razorpayConfig.getKeyId(),
                                                        razorpayConfig.getKeySecret())
                )
                .body(razorpayCreateOrderRequest)
                .retrieve()
                .body(RazorpayCreateOrderResponse.class);
    }
}
