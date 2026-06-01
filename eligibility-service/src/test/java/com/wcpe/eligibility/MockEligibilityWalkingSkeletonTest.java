package com.wcpe.eligibility;

import static org.assertj.core.api.Assertions.assertThat;

import com.wcpe.eligibility.domain.models.MockEligibilityWalkingSkeleton;
import org.junit.jupiter.api.Test;

class MockEligibilityWalkingSkeletonTest {
    private final MockEligibilityWalkingSkeleton skeleton = new MockEligibilityWalkingSkeleton();

    @Test
    void emitsEligibleOutcomeFromReplaceableMockInputs() {
        var result = skeleton.evaluate(
            new MockEligibilityWalkingSkeleton.MockScenarioInput(true),
            new MockEligibilityWalkingSkeleton.MockCatalogInput(true),
            new MockEligibilityWalkingSkeleton.MockRateInput(true));

        assertThat(result.status()).isEqualTo("ELIGIBLE");
        assertThat(result.reasonCodes()).containsExactly("MOCK_INPUTS_ACCEPTED");
    }

    @Test
    void emitsIneligibleOutcomeWithReasonCodeWithoutLiveUpstreams() {
        var result = skeleton.evaluate(
            new MockEligibilityWalkingSkeleton.MockScenarioInput(true),
            new MockEligibilityWalkingSkeleton.MockCatalogInput(false),
            new MockEligibilityWalkingSkeleton.MockRateInput(true));

        assertThat(result.status()).isEqualTo("INELIGIBLE");
        assertThat(result.reasonCodes()).containsExactly("CATALOG_PRODUCT_UNAVAILABLE");
    }
}
