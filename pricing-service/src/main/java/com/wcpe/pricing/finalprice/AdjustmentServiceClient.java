package com.wcpe.pricing.finalprice;

import java.time.Duration;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * WebClient adapter for the real adjustment-service calculation endpoint.
 * The base URL must come from runtime configuration; no local default endpoint is encoded here.
 */
@Component
@ConditionalOnProperty(name = "wcpe.adjustment-service.base-url")
public final class AdjustmentServiceClient implements FinalPriceApi.AdjustmentCalculationPort {
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
    public FinalPriceApi.AdjustmentCalculationResult calculate(FinalPriceApi.AdjustmentCalculationRequest request) {
        return webClient.post()
                .uri("/api/v1/adjustments/calculate")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(FinalPriceApi.AdjustmentCalculationResult.class)
                .block(TIMEOUT);
    }
}
