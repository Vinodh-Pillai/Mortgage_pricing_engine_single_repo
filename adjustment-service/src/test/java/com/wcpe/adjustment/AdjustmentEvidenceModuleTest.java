package com.wcpe.adjustment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wcpe.adjustment.AdjustmentEvidenceModule.AdjustmentBlockedState;
import com.wcpe.adjustment.AdjustmentEvidenceModule.AdjustmentConflictEvidence;
import com.wcpe.adjustment.AdjustmentEvidenceModule.AdjustmentEvidenceLine;
import com.wcpe.adjustment.AdjustmentEvidenceModule.AdjustmentEvidenceRequest;
import com.wcpe.adjustment.AdjustmentEvidenceModule.AdjustmentEvidenceView;
import com.wcpe.adjustment.AdjustmentEvidenceModule.AdjustmentSummaryEvidence;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdjustmentEvidenceModuleTest {
    private final AdjustmentEvidenceModule module = new AdjustmentEvidenceModule();

    @Test
    void exposesIdsFactsSourcesConflictsCompensationHooksAndSummaries() {
        AdjustmentEvidenceView view = module.view(request(true, List.of()));

        assertThat(view.status()).isEqualTo("READY");
        assertThat(view.adjustments()).extracting(AdjustmentEvidenceLine::adjustmentId)
            .containsExactly("adjustment-llpa-contract-required", "adjustment-compensation-hook-contract-required");
        assertThat(view.adjustments().get(0).factRefs()).contains("fact:representative-credit", "fact:loan-to-value");
        assertThat(view.adjustments().get(0).sourceRef()).isEqualTo("adjustment-service.llpa-evaluator");
        assertThat(view.adjustments().get(1).compensationHookRefs()).contains("lo-compensation-hook-ref-required");
        assertThat(view.conflicts()).extracting(AdjustmentConflictEvidence::resolutionOwner).containsExactly("Pricing Operations");
        assertThat(view.summaries()).extracting(AdjustmentSummaryEvidence::category).contains("LLPA", "COMPENSATION");
    }

    @Test
    void missingConfigurationRequiresBlockedStateWithoutValues() {
        AdjustmentEvidenceView view = module.view(request(false, List.of(
            new AdjustmentBlockedState("ADJUSTMENT_CONFIG_MISSING",
                "Configured adjustment source is required before LLPA or fee values can be used.",
                "adjustment-service.configuration", "Pricing Operations"))));

        assertThat(view.status()).isEqualTo("BLOCKED");
        assertThat(view.blockers()).extracting(AdjustmentBlockedState::reasonCode).containsExactly("ADJUSTMENT_CONFIG_MISSING");
        assertThat(view.toString()).doesNotContain("0.125", "fee amount", "rate table");
    }

    @Test
    void missingConfigurationCannotProceedWithoutBlocker() {
        assertThatThrownBy(() -> module.view(request(false, List.of())))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("missing configuration requires at least one blocked state");
    }

    private static AdjustmentEvidenceRequest request(boolean configured, List<AdjustmentBlockedState> blockers) {
        return new AdjustmentEvidenceRequest(
            "ui-preview-tenant",
            configured,
            configured ? "ADJUSTMENT_SERVICE_CONFIGURED" : "ADJUSTMENT_SERVICE_CONFIG_MISSING",
            List.of(
                new AdjustmentEvidenceLine("adjustment-llpa-contract-required", "LLPA evaluator evidence", "LLPA", "VISIBLE",
                    List.of("fact:representative-credit", "fact:loan-to-value"), "adjustment-service.llpa-evaluator",
                    "llpa-rulebook-version-ref-required", "Backend-owned evaluator refs are visible.", List.of(),
                    List.of("conflict-manual-review-required")),
                new AdjustmentEvidenceLine("adjustment-compensation-hook-contract-required", "Compensation hook evidence", "COMPENSATION", "VISIBLE",
                    List.of("fact:channel", "fact:compensation-plan-ref"), "adjustment-service.compensation-hooks",
                    "compensation-hook-version-ref-required", "Compensation hook refs are visible without sensitive amounts.",
                    List.of("lo-compensation-hook-ref-required"), List.of())),
            List.of(new AdjustmentConflictEvidence("conflict-manual-review-required", "BLOCKING", "REQUIRE_MANUAL_REVIEW",
                "Configured conflict policy requires manual review.", "Pricing Operations",
                List.of("adjustment-llpa-contract-required"))),
            blockers,
            List.of(
                new AdjustmentSummaryEvidence("LLPA", "LLPA ids and fact refs are visible.", List.of("llpa-rulebook-version-ref-required")),
                new AdjustmentSummaryEvidence("COMPENSATION", "Compensation hook refs are visible.", List.of("compensation-hook-version-ref-required"))),
            List.of("llpa-rulebook-version-ref-required", "compensation-hook-version-ref-required"),
            List.of("audit:adjustment-evidence-required"),
            "adjustment-evidence-replay-hash-required",
            "corr-adjustment-evidence-test"
        );
    }
}
