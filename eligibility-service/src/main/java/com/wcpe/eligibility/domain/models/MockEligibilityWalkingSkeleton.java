package com.wcpe.eligibility.domain.models;

import java.util.List;
import java.util.Objects;

public class MockEligibilityWalkingSkeleton {
    public MockEligibilityDecision evaluate(MockScenarioInput scenario, MockCatalogInput catalog, MockRateInput rate) {
        Objects.requireNonNull(scenario, "scenario input is required");
        Objects.requireNonNull(catalog, "catalog input is required");
        Objects.requireNonNull(rate, "rate input is required");

        if (!scenario.inScope()) {
            return new MockEligibilityDecision("INELIGIBLE", List.of("SCENARIO_OUT_OF_SCOPE"));
        }
        if (!catalog.productAvailable()) {
            return new MockEligibilityDecision("INELIGIBLE", List.of("CATALOG_PRODUCT_UNAVAILABLE"));
        }
        if (!rate.rateAvailable()) {
            return new MockEligibilityDecision("INELIGIBLE", List.of("RATE_FEED_UNAVAILABLE"));
        }
        return new MockEligibilityDecision("ELIGIBLE", List.of("MOCK_INPUTS_ACCEPTED"));
    }

    public record MockScenarioInput(boolean inScope) {}
    public record MockCatalogInput(boolean productAvailable) {}
    public record MockRateInput(boolean rateAvailable) {}
    public record MockEligibilityDecision(String status, List<String> reasonCodes) {
        public MockEligibilityDecision {
            reasonCodes = List.copyOf(reasonCodes);
        }
    }
}
