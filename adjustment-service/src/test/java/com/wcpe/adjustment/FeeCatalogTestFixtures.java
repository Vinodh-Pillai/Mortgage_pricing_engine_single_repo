package com.wcpe.adjustment;

import com.wcpe.adjustment.FeeCatalogVersion.CalculationMethod;
import com.wcpe.adjustment.FeeCatalogVersion.FeeApplicability;
import com.wcpe.adjustment.FeeCatalogVersion.FeeCatalogRequestContext;
import com.wcpe.adjustment.FeeCatalogVersion.FeeDefinition;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class FeeCatalogTestFixtures {
    private FeeCatalogTestFixtures() {
    }

    static FeeDefinition fixedAmountFee(String feeCode) {
        return fee(feeCode, CalculationMethod.FIXED_AMOUNT, Map.of("amountConfigRef", "configured-amount-ref"));
    }

    static FeeDefinition waivedFee(String feeCode) {
        return fee(feeCode, CalculationMethod.WAIVED, Map.of("waiverPolicyRef", "configured-waiver-ref"));
    }

    static FeeDefinition fee(String feeCode, CalculationMethod method, Map<String, String> formulaParameters) {
        return new FeeDefinition(
            UUID.nameUUIDFromBytes(feeCode.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
            feeCode,
            "Configured " + feeCode,
            "Synthetic fee definition for catalog validation",
            "CONFIGURED_CATEGORY",
            "CONFIGURED_PAYER",
            "CONFIGURED_PAYEE",
            true,
            true,
            "CONFIGURED_TOLERANCE_BUCKET",
            "CONFIGURED_DISCLOSURE_SECTION",
            method,
            formulaParameters,
            configuredApplicability(),
            "CONFIGURED_REASON",
            "source-policy-ref",
            true,
            null
        );
    }

    static FeeDefinition incompleteActiveFee() {
        return new FeeDefinition(
            UUID.fromString("30000000-0000-0000-0000-000000000066"),
            "CONFIGURED_FEE_INCOMPLETE",
            "Incomplete fee",
            "Synthetic incomplete fee",
            null,
            null,
            null,
            false,
            false,
            null,
            null,
            CalculationMethod.FIXED_AMOUNT,
            Map.of(),
            new FeeApplicability(List.of(), List.of(), List.of(), List.of()),
            null,
            "source-policy-ref",
            true,
            null
        );
    }

    static FeeApplicability configuredApplicability() {
        return new FeeApplicability(
            List.of("configured-product"),
            List.of("configured-investor"),
            List.of("configured-channel"),
            List.of("configured-jurisdiction")
        );
    }

    static FeeCatalogRequestContext context(UUID tenantId) {
        return new FeeCatalogRequestContext(
            tenantId,
            "configured-product",
            "configured-investor",
            "configured-channel",
            "configured-jurisdiction",
            Instant.parse("2026-02-01T00:00:00Z")
        );
    }
}
