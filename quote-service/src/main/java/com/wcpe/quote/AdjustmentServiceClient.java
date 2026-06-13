package com.wcpe.quote;

import java.time.Duration;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/** WebClient adapter for real adjustment-service calculations during quote launch. */
@Component
@ConditionalOnProperty(name = "wcpe.adjustment-service.base-url")
public final class AdjustmentServiceClient implements AdjustmentCalculationPort {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final WebClient webClient;

    @Autowired
    public AdjustmentServiceClient(WebClient.Builder builder,
            @Value("${wcpe.adjustment-service.base-url}") String adjustmentServiceBaseUrl) {
        if (adjustmentServiceBaseUrl == null || adjustmentServiceBaseUrl.isBlank()) {
            throw new IllegalArgumentException("wcpe.adjustment-service.base-url is required");
        }
        this.webClient = builder.baseUrl(adjustmentServiceBaseUrl).build();
    }

    public AdjustmentServiceClient(WebClient webClient) {
        this.webClient = Objects.requireNonNull(webClient, "webClient is required");
    }

    @Override
    public AdjustmentCalculationResult calculate(AdjustmentCalculationRequest request) {
        return webClient.post()
            .uri("/api/v1/adjustments/calculate")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(AdjustmentCalculationResult.class)
            .block(TIMEOUT);
    }
}
