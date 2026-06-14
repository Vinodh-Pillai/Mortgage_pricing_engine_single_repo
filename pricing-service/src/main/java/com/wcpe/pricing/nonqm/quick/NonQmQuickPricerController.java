package com.wcpe.pricing.nonqm.quick;

import com.wcpe.pricing.nonqm.quick.NonQmQuickPricerApi.NonQmQuickQuoteHeaders;
import com.wcpe.pricing.nonqm.quick.NonQmQuickPricerApi.NonQmQuickQuoteRequest;
import com.wcpe.pricing.nonqm.quick.NonQmQuickPricerApi.QuickPricerUiConfiguration;
import com.wcpe.pricing.nonqm.quick.NonQmQuickPricerApi.QuickQuoteResult;
import com.wcpe.pricing.nonqm.quick.NonQmQuickPricerApi.ScenarioReference;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/quotes/non-qm/quick")
public final class NonQmQuickPricerController {
    private final NonQmQuickPricerApi api;

    public NonQmQuickPricerController() {
        this(new NonQmQuickPricerApi());
    }

    NonQmQuickPricerController(NonQmQuickPricerApi api) {
        this.api = api;
    }

    @PostMapping
    public ResponseEntity<QuickQuoteResult> quote(
            @RequestHeader(name = "X-Pricing-Permissions", required = false) String permissions,
            @RequestHeader(name = "X-Actor-Id") String actorId,
            @RequestHeader(name = "X-Correlation-Id") String correlationId,
            @RequestBody NonQmQuickQuoteRequest request) {
        return ResponseEntity.ok(api.quote(request.tenantId(), NonQmQuickQuoteHeaders.of(permissions, actorId, correlationId), request));
    }

    @GetMapping("/{quickQuoteId}")
    public ResponseEntity<QuickQuoteResult> getQuote(
            @PathVariable String quickQuoteId,
            @RequestParam String tenantId,
            @RequestHeader(name = "X-Pricing-Permissions", required = false) String permissions,
            @RequestHeader(name = "X-Actor-Id") String actorId,
            @RequestHeader(name = "X-Correlation-Id") String correlationId) {
        return ResponseEntity.ok(api.getQuote(tenantId, quickQuoteId, NonQmQuickQuoteHeaders.of(permissions, actorId, correlationId)));
    }

    @PostMapping("/{quickQuoteId}/continue")
    public ResponseEntity<ScenarioReference> continueToFullScenario(
            @PathVariable String quickQuoteId,
            @RequestParam String tenantId,
            @RequestHeader(name = "X-Pricing-Permissions", required = false) String permissions,
            @RequestHeader(name = "X-Actor-Id") String actorId,
            @RequestHeader(name = "X-Correlation-Id") String correlationId) {
        return ResponseEntity.ok(api.continueToFullScenario(tenantId, quickQuoteId,
                NonQmQuickQuoteHeaders.of(permissions, actorId, correlationId)));
    }

    @GetMapping("/ui")
    public ResponseEntity<QuickPricerUiConfiguration> uiConfiguration() {
        return ResponseEntity.ok(api.uiConfiguration());
    }
}
