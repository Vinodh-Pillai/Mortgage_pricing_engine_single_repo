package com.wcpe.adjustment;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class AdjustmentServiceTest {

    AdjustmentService service = new AdjustmentService();

    @Test
    void mockBasePriceDecisionMapsToExplicitAdjustmentLinesAndDerivedTotal() {
        BasePriceDecisionStub baseDecision = new BasePriceDecisionStub(
            "SCN-ADJ-001",
            "BP-001",
            "fixed-rate-30yr",
            300000.0,
            "USD",
            "mock"
        );

        List<AdjustmentFactor> factors = List.of(
            new AdjustmentFactor("risk-adjustment", 1500.0, "mock risk fixture"),
            new AdjustmentFactor("low-doc-fee", 750.0, "mock low-doc fixture"),
            new AdjustmentFactor("credit-enhancement", -500.0, "mock credit enhancement")
        );

        AdjustmentCalculationRequest request = new AdjustmentCalculationRequest(
            baseDecision,
            Map.of("ltvBand", "mock-high", "creditBand", "mock-good"),
            factors,
            "mock-pii-06"
        );

        AdjustmentCalculationResult result = service.calculate(request);

        assertThat(result.scenarioId()).isEqualTo("SCN-ADJ-001");
        assertThat(result.basePriceId()).isEqualTo("BP-001");
        assertThat(result.adjustments()).hasSize(3);
        assertThat(result.adjustments().get(0).factorKey()).isEqualTo("risk-adjustment");
        assertThat(result.adjustments().get(0).amount()).isEqualTo(1500.0);
        assertThat(result.adjustments().get(0).source()).isEqualTo("mock");
        assertThat(result.adjustments().get(1).factorKey()).isEqualTo("low-doc-fee");
        assertThat(result.adjustments().get(2).factorKey()).isEqualTo("credit-enhancement");
        assertThat(result.totalAdjustment()).isEqualTo(1750.0);
        assertThat(result.referenceDataVersion()).isEqualTo("mock-pii-06");
        assertThat(result.calculationMode()).isEqualTo("mock");
    }

    @Test
    void emptyFactorsProducesZeroTotal() {
        BasePriceDecisionStub baseDecision = new BasePriceDecisionStub(
            "SCN-ADJ-002",
            "BP-002",
            "arm-5yr",
            200000.0,
            "USD",
            "mock"
        );

        AdjustmentCalculationRequest request = new AdjustmentCalculationRequest(
            baseDecision,
            Map.of(),
            List.of(),
            "mock-pii-06"
        );

        AdjustmentCalculationResult result = service.calculate(request);

        assertThat(result.adjustments()).isEmpty();
        assertThat(result.totalAdjustment()).isZero();
        assertThat(result.calculationMode()).isEqualTo("mock");
    }

    @Test
    void singleFactorTotalEqualsFactorAmount() {
        BasePriceDecisionStub baseDecision = new BasePriceDecisionStub(
            "SCN-ADJ-003",
            "BP-003",
            "fixed-rate-15yr",
            150000.0,
            "USD",
            "mock"
        );

        List<AdjustmentFactor> factors = List.of(
            new AdjustmentFactor("conforming-limit-adj", 200.0, "mock conforming limit")
        );

        AdjustmentCalculationRequest request = new AdjustmentCalculationRequest(
            baseDecision,
            Map.of(),
            factors,
            null
        );

        AdjustmentCalculationResult result = service.calculate(request);

        assertThat(result.adjustments()).hasSize(1);
        assertThat(result.totalAdjustment()).isEqualTo(200.0);
        assertThat(result.referenceDataVersion()).isEqualTo("mock-pii-06");
    }

    @Test
    void negativeFactorsReduceTotalCorrectly() {
        BasePriceDecisionStub baseDecision = new BasePriceDecisionStub(
            "SCN-ADJ-004",
            "BP-004",
            "fixed-rate-30yr",
            400000.0,
            "USD",
            "mock"
        );

        List<AdjustmentFactor> factors = List.of(
            new AdjustmentFactor("risk-penalty", 1000.0, "mock risk penalty"),
            new AdjustmentFactor("lpa-offset", -1200.0, "mock LPA offset")
        );

        AdjustmentCalculationRequest request = new AdjustmentCalculationRequest(
            baseDecision,
            Map.of(),
            factors,
            "mock-pii-06"
        );

        AdjustmentCalculationResult result = service.calculate(request);

        assertThat(result.totalAdjustment()).isEqualTo(-200.0);
    }

    @Test
    void invalidSourceThrows() {
        assertThatThrownBy(() -> new BasePriceDecisionStub(
            "SCN", "BP", "basis", 100.0, "USD", "production"
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("mock");
    }

    @Test
    void calculationIsDeterministic() {
        BasePriceDecisionStub baseDecision = new BasePriceDecisionStub(
            "SCN-ADJ-005", "BP-005", "fixed-rate-30yr", 250000.0, "USD", "mock"
        );
        List<AdjustmentFactor> factors = List.of(
            new AdjustmentFactor("mock-factor-a", 100.0, "reason-a")
        );
        AdjustmentCalculationRequest request = new AdjustmentCalculationRequest(
            baseDecision, Map.of(), factors, "mock-pii-06"
        );

        AdjustmentCalculationResult r1 = service.calculate(request);
        AdjustmentCalculationResult r2 = service.calculate(request);

        assertThat(r2.totalAdjustment()).isEqualTo(r1.totalAdjustment());
        assertThat(r2.scenarioId()).isEqualTo(r1.scenarioId());
        assertThat(r2.adjustments()).isEqualTo(r1.adjustments());
    }
}
