package com.wcpe.adjustment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class FeeDefinitionInvariantTest {
    @Test
    void feeCodeMustBeUniquePerTenantCatalogVersion() {
        assertThatThrownBy(() -> FeeCatalogLifecycleTest.draftCatalog(List.of(
            FeeCatalogTestFixtures.fixedAmountFee("CONFIGURED_FEE_A"),
            FeeCatalogTestFixtures.waivedFee("CONFIGURED_FEE_A")
        )).validateDefinitions())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("duplicate fee code");
    }

    @Test
    void activeFeesRequireDisclosureToleranceReasonAndApplicabilityMetadata() {
        assertThatThrownBy(() -> FeeCatalogLifecycleTest.draftCatalog(List.of(
            FeeCatalogTestFixtures.incompleteActiveFee()
        )).validateDefinitions())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("active fee definition requires category")
            .hasMessageContaining("active fee definition requires reasonCode")
            .hasMessageContaining("active fee definition requires toleranceBucket")
            .hasMessageContaining("active fee definition requires disclosureSection")
            .hasMessageContaining("active fee definition requires at least one applicability selector");
    }
}
