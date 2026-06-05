package com.wcpe.adjustment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wcpe.adjustment.FicoLtvLlpaEvaluator.BoundaryPolicy;
import com.wcpe.adjustment.FicoLtvLlpaEvaluator.EvaluationRequest;
import com.wcpe.adjustment.FicoLtvLlpaEvaluator.EvaluationResult;
import com.wcpe.adjustment.FicoLtvLlpaEvaluator.EvaluationStatus;
import com.wcpe.adjustment.FicoLtvLlpaEvaluator.GridCell;
import com.wcpe.adjustment.FicoLtvLlpaEvaluator.LedgerEntry;
import com.wcpe.adjustment.FicoLtvLlpaEvaluator.LtvMetric;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FicoLtvLlpaEvaluatorTest {
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000006");
    private static final UUID RULE_BOOK_ID = UUID.fromString("20000000-0000-0000-0000-000000000006");
    private static final UUID RULE_ID = UUID.fromString("30000000-0000-0000-0000-000000000006");
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void FicoLtvLlpaEvaluatorTest_appliesConfiguredGridCellWithoutHardcodedLlpaValues() {
        EvaluationResult result = new FicoLtvLlpaEvaluator().evaluate(request(742, "82.500000"), List.of(cell(
            "CONFIGURED_FICO_BAND_A",
            720,
            759,
            "CONFIGURED_LTV_BAND_A",
            "80.000000",
            "85.000000",
            "0.125000",
            1
        )));

        assertThat(result.status()).isEqualTo(EvaluationStatus.APPLIED);
        assertThat(result.category()).isEqualTo("LLPA_FICO_LTV");
        assertThat(result.pointsDelta()).isEqualByComparingTo("0.125000");
        assertThat(result.bpsDelta()).isEqualByComparingTo("12.5000");
        assertThat(result.moneyAmount()).isEqualByComparingTo("312.50");
        assertThat(result.selectedFicoBandKey()).isEqualTo("CONFIGURED_FICO_BAND_A");
        assertThat(result.selectedLtvBandKey()).isEqualTo("CONFIGURED_LTV_BAND_A");
        assertThat(result.inputSnapshotHash()).hasSize(64);
    }

    @Test
    void RepresentativeFicoMissingTest_failsClosedWhenRepresentativeFicoIsMissing() {
        EvaluationResult result = new FicoLtvLlpaEvaluator().evaluate(request(null, "82.500000"), List.of(cell(
            "CONFIGURED_FICO_BAND_A", 720, 759, "CONFIGURED_LTV_BAND_A", "80.000000", "85.000000", "0.125000", 1
        )));

        assertThat(result.status()).isEqualTo(EvaluationStatus.FAIL_CLOSED);
        assertThat(result.validationMessages()).containsExactly("representative FICO is required");
        assertThat(result.pointsDelta()).isZero();
    }

    @Test
    void FicoLtvBandBoundaryTest_matchesConfiguredBoundaryPolicyAndRejectsOverlappingGrid() {
        GridCell openLower = new GridCell(
            TENANT_ID,
            RULE_BOOK_ID,
            UUID.fromString("30000000-0000-0000-0000-000000000007"),
            "v1",
            "rule-book-hash",
            "configured-product",
            "configured-investor",
            "configured-channel",
            "CONFIGURED_FICO_BAND_B",
            760,
            779,
            LtvMetric.LTV,
            "CONFIGURED_LTV_BAND_B",
            new BigDecimal("85.000000"),
            new BigDecimal("90.000000"),
            BoundaryPolicy.MIN_EXCLUSIVE_MAX_INCLUSIVE,
            new BigDecimal("0.250000"),
            "CONFIGURED_REASON_B",
            2,
            START,
            null,
            null,
            true
        );

        EvaluationResult boundaryResult = new FicoLtvLlpaEvaluator().evaluate(request(765, "85.000000"), List.of(openLower));

        assertThat(boundaryResult.status()).isEqualTo(EvaluationStatus.FAIL_CLOSED);
        assertThatThrownBy(() -> FicoLtvLlpaEvaluator.validateNoOverlaps(List.of(
            cell("FICO_A", 720, 759, "LTV_A", "80.000000", "85.000000", "0.125000", 1),
            cell("FICO_B", 740, 779, "LTV_B", "84.000000", "90.000000", "0.250000", 2)
        )))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("overlapping FICO/LTV grid cells");
    }

    @Test
    void FicoLtvPrecisionTest_roundsPointsBpsMoneyAndLedgerDeterministically() {
        EvaluationResult first = new FicoLtvLlpaEvaluator().evaluate(request(742, "82.500000"), List.of(cell(
            "CONFIGURED_FICO_BAND_A", 720, 759, "CONFIGURED_LTV_BAND_A", "80.000000", "85.000000", "0.1266666", 1
        )));
        EvaluationResult second = new FicoLtvLlpaEvaluator().evaluate(request(742, "82.500000"), List.of(cell(
            "CONFIGURED_FICO_BAND_A", 720, 759, "CONFIGURED_LTV_BAND_A", "80.000000", "85.000000", "0.1266666", 1
        )));
        LedgerEntry ledger = LedgerEntry.from(TENANT_ID, "QUOTE-PII-06-S02", first, 30);

        assertThat(first.adjustmentId()).isEqualTo(second.adjustmentId());
        assertThat(first.pointsDelta()).isEqualByComparingTo("0.126667");
        assertThat(first.bpsDelta()).isEqualByComparingTo("12.6667");
        assertThat(first.moneyAmount()).isEqualByComparingTo("316.67");
        assertThat(ledger.ruleType()).isEqualTo("FICO_LTV_LLPA");
        assertThat(ledger.waterfallSequence()).isEqualTo(30);
        assertThat(ledger.contentHash()).hasSize(64);
    }

    private static EvaluationRequest request(Integer representativeFico, String ltv) {
        return new EvaluationRequest(
            TENANT_ID,
            "QUOTE-PII-06-S02",
            "SCENARIO-PII-06-S02",
            "configured-product",
            "configured-investor",
            "configured-channel",
            Instant.parse("2026-02-01T00:00:00Z"),
            new BigDecimal("250000.00"),
            new BigDecimal("100.000000"),
            representativeFico,
            new BigDecimal(ltv),
            null,
            null,
            "v1"
        );
    }

    private static GridCell cell(
        String ficoBandKey,
        int ficoMin,
        int ficoMax,
        String ltvBandKey,
        String ltvMin,
        String ltvMax,
        String pointsDelta,
        int priority
    ) {
        return new GridCell(
            TENANT_ID,
            RULE_BOOK_ID,
            RULE_ID,
            "v1",
            "rule-book-hash",
            "configured-product",
            "configured-investor",
            "configured-channel",
            ficoBandKey,
            ficoMin,
            ficoMax,
            LtvMetric.LTV,
            ltvBandKey,
            new BigDecimal(ltvMin),
            new BigDecimal(ltvMax),
            BoundaryPolicy.MIN_INCLUSIVE_MAX_INCLUSIVE,
            new BigDecimal(pointsDelta),
            "CONFIGURED_REASON_A",
            priority,
            START,
            null,
            null,
            true
        );
    }
}
