package com.wcpe.pricing.homeequity;

import com.wcpe.pricing.homeequity.HomeEquityPricingApi.HomeEquityPriceRequest;
import com.wcpe.pricing.homeequity.HomeEquityPricingApi.HomeEquityPriceResponse;
import com.wcpe.pricing.homeequity.HomeEquityPricingApi.HomeEquityPricingHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/home-equity")
public final class HomeEquityPricingController {
    private final HomeEquityPricingApi api;

    public HomeEquityPricingController() {
        this(new HomeEquityPricingApi());
    }

    HomeEquityPricingController(HomeEquityPricingApi api) {
        this.api = api;
    }

    @PostMapping("/price")
    public ResponseEntity<HomeEquityPriceResponse> price(
            @PathVariable String tenantId,
            @RequestHeader(name = "X-Pricing-Permissions", required = false) String permissions,
            @RequestHeader(name = "X-Actor-Id") String actorId,
            @RequestHeader(name = "X-Correlation-Id") String correlationId,
            @RequestBody HomeEquityPriceRequest request) {
        HomeEquityPriceResponse response = api.price(tenantId,
                new HomeEquityPricingHeaders(parsePermissions(permissions), actorId, correlationId), request);
        return ResponseEntity.ok(response);
    }

    private static Set<String> parsePermissions(String permissions) {
        if (permissions == null || permissions.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(permissions.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }
}
