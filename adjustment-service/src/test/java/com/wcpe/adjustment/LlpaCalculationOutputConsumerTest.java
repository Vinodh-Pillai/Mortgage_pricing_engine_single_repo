package com.wcpe.adjustment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wcpe.adjustment.LlpaCalculationOutputConsumer.CalculationOutputSnapshot;
import com.wcpe.adjustment.LlpaCalculationOutputConsumer.ConsumerRequest;
import com.wcpe.adjustment.LlpaCalculationOutputConsumer.CrossModuleTenantContext;
import com.wcpe.adjustment.LlpaCalculationOutputConsumer.LlpaOutputBinding;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LlpaCalculationOutputConsumerTest {
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000074");
    private static final Instant QUOTE_DATE = Instant.parse("2026-06-19T00:00:00Z");

    @Test
    void configuredLlpaCalculationOutputsBecomeStructuredAdjustmentLinesWithTableVersionTrace() {
        AdjustmentCalculationResult result = new LlpaCalculationOutputConsumer().consume(request(
            snapshot(Map.of(
                "calc.llpa.fico-ltv.points", new BigDecimal("-0.125000"),
                "calc.llpa.cashout.points", new BigDecimal("0.250000")
            )),
            List.of(
                binding("00000000-0000-0000-0000-000000000701", "calc.llpa.fico-ltv.points", "LLPA_FICO_LTV", "table:llpa-fico-ltv:v2026.06", 1),
                binding("00000000-0000-0000-0000-000000000702", "calc.llpa.cashout.points", "LLPA_CASH_OUT", "table:llpa-cashout:v2026.06", 2)
            )
        ));

        assertThat(result.blocked()).isFalse();
        assertThat(result.calculationMode()).isEqualTo("calculation-runtime-llpa");
        assertThat(result.adjustments()).extracting(AdjustmentLine::factorKey)
            .containsExactly("LLPA_FICO_LTV", "LLPA_CASH_OUT");
        assertThat(result.totalAdjustment()).isEqualTo(0.125000d);
        assertThat(result.adjustments().get(0).sourceRef()).isEqualTo("table:llpa-fico-ltv:v2026.06");
        assertThat(result.auditRefs()).allMatch(ref -> ref.contains("tenant:" + TENANT_ID)
            && ref.contains("runtime-eval-PII-74-S02") && ref.contains("versions:calc-version-PII-71-S04"));
        assertThat(result.auditRefs().get(0)).contains("table:llpa-fico-ltv:v2026.06");
        assertThat(result.totalsByType())
            .containsEntry("CALCULATION_EVALUATION_ID", "runtime-eval-PII-74-S02");
        assertThat(result.totalsByType().get("LLPA_OUTPUT_REFS").toString())
            .contains("calc.llpa.fico-ltv.points", "calc.llpa.cashout.points");
    }

    @Test
    void noApprovedLlpaBindingFailsClosedWithoutInventingValues() {
        AdjustmentCalculationResult result = new LlpaCalculationOutputConsumer().consume(request(snapshot(Map.of()), List.of()));

        assertThat(result.blocked()).isTrue();
        assertThat(result.totalAdjustment()).isZero();
        assertThat(result.adjustments()).singleElement().satisfies(line -> {
            assertThat(line.factorKey()).isEqualTo("NO_APPROVED_LLPA_OUTPUT_BINDING");
            assertThat(line.amount()).isZero();
            assertThat(line.applied()).isFalse();
        });
    }

    @Test
    void missingConfiguredOutputRefFailsClosedAndPreservesStableFieldVersions() {
        AdjustmentCalculationResult result = new LlpaCalculationOutputConsumer().consume(request(
            snapshot(Map.of("calc.llpa.fico-ltv.points", new BigDecimal("0.125000"))),
            List.of(binding("00000000-0000-0000-0000-000000000703", "calc.llpa.missing.points", "LLPA_MISSING", "table:llpa-missing:v2026.06", 1))
        ));

        assertThat(result.blocked()).isTrue();
        assertThat(result.adjustments()).singleElement().satisfies(line -> {
            assertThat(line.outputType()).isEqualTo("BLOCKING_CONFLICT");
            assertThat(line.warnings()).containsExactly("MISSING_LLPA_CALCULATION_OUTPUT:calc.llpa.missing.points");
        });
        assertThat(result.totalsByType().get("FIELD_VERSION_REFS").toString())
            .contains("field:representativeFico=v2026.06-active", "field:ltv=v2026.06-active");
        assertThat(result.totalsByType().get("INPUT_FIELD_REFS").toString())
            .contains("field:representativeFico", "field:ltv");
    }

    @Test
    void explicitCalculationSnapshotIsRequired() {
        assertThatThrownBy(() -> new LlpaCalculationOutputConsumer().consume(request(
            new CalculationOutputSnapshot("not-supplied", List.of(), Map.of(), Map.of(), Map.of(), Map.of()),
            List.of(binding("00000000-0000-0000-0000-000000000704", "calc.llpa.points", "LLPA", "table:v1", 1))
        )))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("explicit calculation evaluation snapshot");
    }

    @Test
    void rejectsCalculationOutputFromAnotherTenantBeforeAdjustmentLinesAreCreated() {
        assertThatThrownBy(() -> new LlpaCalculationOutputConsumer().consume(new ConsumerRequest(
            TENANT_ID,
            new BasePriceDecisionStub("scenario-PII-76-S03", "base-price-PII-76-S03", "configured-product", 450000.0, "USD", "pricing-service"),
            QUOTE_DATE,
            "active-field-library-v2026.06",
            snapshot(Map.of("calc.llpa.fico-ltv.points", new BigDecimal("0.125000"))),
            List.of(binding("00000000-0000-0000-0000-000000000705", "calc.llpa.fico-ltv.points", "LLPA_FICO_LTV", "table:llpa-fico-ltv:v2026.06", 1)),
            new CrossModuleTenantContext("20000000-0000-0000-0000-000000000074", "calculation-runtime", "runtime-eval-PII-76-S03")
        )))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("TENANT_CONTEXT_MISMATCH");
    }

    private static ConsumerRequest request(CalculationOutputSnapshot snapshot, List<LlpaOutputBinding> bindings) {
        return new ConsumerRequest(
            TENANT_ID,
            new BasePriceDecisionStub("scenario-PII-74-S02", "base-price-PII-74-S02", "configured-product", 450000.0, "USD", "pricing-service"),
            QUOTE_DATE,
            "active-field-library-v2026.06",
            snapshot,
            bindings
        );
    }

    private static CalculationOutputSnapshot snapshot(Map<String, BigDecimal> outputs) {
        return new CalculationOutputSnapshot(
            "runtime-eval-PII-74-S02",
            List.of("calc-version-PII-71-S04"),
            outputs,
            Map.of(
                "field:representativeFico", "742",
                "field:ltv", "82.500000"
            ),
            Map.of(
                "field:representativeFico", "v2026.06-active",
                "field:ltv", "v2026.06-active"
            ),
            Map.of(
                "calc.llpa.fico-ltv.points", "table:llpa-fico-ltv:v2026.06",
                "calc.llpa.cashout.points", "table:llpa-cashout:v2026.06"
            )
        );
    }

    private static LlpaOutputBinding binding(String id, String outputRef, String factorKey, String tableVersionRef, int priority) {
        return new LlpaOutputBinding(UUID.fromString(id), outputRef, factorKey, factorKey, "POINTS_DELTA", tableVersionRef,
            factorKey + " configured output", priority, true);
    }
}
