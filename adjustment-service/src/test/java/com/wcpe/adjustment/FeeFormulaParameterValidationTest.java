package com.wcpe.adjustment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wcpe.adjustment.FeeCatalogVersion.CalculationMethod;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FeeFormulaParameterValidationTest {
    @Test
    void configurableMethodsRequireConfigurationReferencesInsteadOfSourceEmbeddedAmounts() {
        assertThatThrownBy(() -> FeeCatalogLifecycleTest.draftCatalog(List.of(
            FeeCatalogTestFixtures.fee("CONFIGURED_FEE_A", CalculationMethod.FIXED_AMOUNT, Map.of())
        )).validateDefinitions())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("FIXED_AMOUNT requires formula parameter amountConfigRef");

        FeeCatalogLifecycleTest.draftCatalog(List.of(
            FeeCatalogTestFixtures.fee("CONFIGURED_FEE_A", CalculationMethod.FIXED_AMOUNT, Map.of("amountConfigRef", "fee-config-ref")),
            FeeCatalogTestFixtures.fee("CONFIGURED_FEE_B", CalculationMethod.PERCENT_OF_LOAN_AMOUNT, Map.of("percentConfigRef", "percent-config-ref")),
            FeeCatalogTestFixtures.fee("CONFIGURED_FEE_C", CalculationMethod.BPS_OF_LOAN_AMOUNT, Map.of("bpsConfigRef", "bps-config-ref")),
            FeeCatalogTestFixtures.fee("CONFIGURED_FEE_D", CalculationMethod.PER_UNIT, Map.of("unitAmountConfigRef", "unit-config-ref")),
            FeeCatalogTestFixtures.fee("CONFIGURED_FEE_E", CalculationMethod.PASS_THROUGH, Map.of("trustedSourceRef", "trusted-source-ref")),
            FeeCatalogTestFixtures.fee("CONFIGURED_FEE_F", CalculationMethod.MANUAL_INPUT_ALLOWED, Map.of("manualInputPermissionRef", "permission-ref", "auditReasonRequired", "true"))
        )).validateDefinitions();
    }

    @Test
    void approvedFormulaExpressionsMustUseApprovedCatalogReferences() {
        assertThatThrownBy(() -> FeeCatalogLifecycleTest.draftCatalog(List.of(
            FeeCatalogTestFixtures.fee("CONFIGURED_FEE_A", CalculationMethod.FORMULA_EXPRESSION_APPROVED, Map.of(
                "expression", "loanAmount * configuredRate",
                "approvedVariablesRef", "variable-ref",
                "approvedFunctionsRef", "function-ref"
            ))
        )).validateDefinitions())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("approvedExpressionRef")
            .hasMessageContaining("approved expression catalog entry");

        FeeCatalogLifecycleTest.draftCatalog(List.of(
            FeeCatalogTestFixtures.fee("CONFIGURED_FEE_A", CalculationMethod.FORMULA_EXPRESSION_APPROVED, Map.of(
                "approvedExpressionRef", "expression-catalog-ref",
                "approvedVariablesRef", "variable-catalog-ref",
                "approvedFunctionsRef", "function-catalog-ref"
            ))
        )).validateDefinitions();
    }
}
