package com.wcpe.adjustment;

import static org.assertj.core.api.Assertions.assertThat;

import com.wcpe.adjustment.AdjustmentRuleBook.EffectiveWindow;
import com.wcpe.adjustment.AdjustmentRuleBook.PricingPrecisionPolicy;
import com.wcpe.adjustment.AdjustmentRuleBook.RuleBookSelector;
import com.wcpe.adjustment.AdjustmentRuleBook.RuleBookStatus;
import com.wcpe.adjustment.RuleBookResolver.InMemoryRuleBookRepository;
import com.wcpe.adjustment.overlay.OverlayPolicyType;
import com.wcpe.adjustment.overlay.OverlayRule;
import com.wcpe.adjustment.overlay.OverlayRuleRepository.InMemoryOverlayRuleRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OverlayTest {
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000034");
    private static final UUID RULE_BOOK_ID = UUID.fromString("20000000-0000-0000-0000-000000000034");
    private static final RuleBookSelector SELECTOR = new RuleBookSelector("CONVENTIONAL", "FNMA", "RETAIL");
    private static final Instant QUOTE_DATE = Instant.parse("2026-06-13T00:00:00Z");

    @Test
    void escrowWaiverCalculation() {
        AdjustmentCalculationResult result = calculate(List.of(rule(OverlayPolicyType.ESCROW_WAIVER, "0.125", "0.000", "0.250", 10, null)),
            facts("escrowWaived", true));

        assertThat(result.adjustments()).extracting(AdjustmentLine::factorKey).contains("ESCROW_WAIVER");
        assertThat(result.totalAdjustment()).isEqualTo(0.00125d);
    }

    @Test
    void armCapsCalculation() {
        AdjustmentCalculationResult result = calculate(List.of(rule(OverlayPolicyType.ARM_CAPS, "0.125", "0.000", "0.250", 10, null)),
            facts("armCaps", "2/2/6"));

        assertThat(result.adjustments()).singleElement().extracting(AdjustmentLine::label).isEqualTo("ARM Caps/Floors");
    }

    @Test
    void buydownCalculation() {
        AdjustmentCalculationResult result = calculate(List.of(rule(OverlayPolicyType.BUYDOWN, "2.500", "0.000", "3.500", 10, null)),
            facts("buydownType", "2-1"));

        assertThat(result.totalAdjustment()).isEqualTo(0.025d);
    }

    @Test
    void highBalanceCalculation() {
        AdjustmentCalculationResult result = calculate(List.of(rule(OverlayPolicyType.HIGH_BALANCE, "0.375", "0.125", "0.375", 10, null)),
            facts("highBalance", true));

        assertThat(result.adjustments()).extracting(AdjustmentLine::factorKey).containsExactly("HIGH_BALANCE");
    }

    @Test
    void jumboMarginAdjustment() {
        assertThat(OverlayPolicyType.JUMBO.waterfallPosition()).isEqualTo(OverlayPolicyType.WaterfallPosition.MARGIN_COMPONENT);
    }

    @Test
    void oddTermCalculation() {
        AdjustmentCalculationResult result = calculate(List.of(rule(OverlayPolicyType.ODD_TERM, "-0.125", "-0.250", "0.125", 10, null)),
            facts("loanTermMonths", 120));

        assertThat(result.totalAdjustment()).isEqualTo(-0.00125d);
    }

    @Test
    void waterfallPositionCorrect() {
        assertThat(OverlayPolicyType.values()).extracting(OverlayPolicyType::waterfallPosition)
            .containsExactly(OverlayPolicyType.WaterfallPosition.LLPA_ADJUSTMENT,
                OverlayPolicyType.WaterfallPosition.LLPA_ADJUSTMENT,
                OverlayPolicyType.WaterfallPosition.LLPA_ADJUSTMENT,
                OverlayPolicyType.WaterfallPosition.LLPA_ADJUSTMENT,
                OverlayPolicyType.WaterfallPosition.MARGIN_COMPONENT,
                OverlayPolicyType.WaterfallPosition.LLPA_ADJUSTMENT);
    }

    @Test
    void exclusivityGroupEnforced() {
        AdjustmentCalculationResult result = calculate(List.of(
            rule(OverlayPolicyType.BUYDOWN, "2.000", "0.000", "3.500", 5, "buydown-family"),
            rule(OverlayPolicyType.BUYDOWN, "3.000", "0.000", "3.500", 10, "buydown-family")),
            facts("buydownType", "3-2-1"));

        assertThat(result.adjustments()).hasSize(1);
        assertThat(result.totalAdjustment()).isEqualTo(0.03d);
    }

    @Test
    void minMaxBoundsEnforced() {
        AdjustmentCalculationResult result = calculate(List.of(rule(OverlayPolicyType.HIGH_BALANCE, "0.500", "0.125", "0.375", 10, null)),
            facts("highBalance", true));

        assertThat(result.totalAdjustment()).isEqualTo(0.00375d);
    }

    @Test
    void fullWaterfallIncludesFiveLlpaOverlayLines() {
        AdjustmentCalculationResult result = calculate(List.of(
            rule(OverlayPolicyType.ESCROW_WAIVER, "0.125", "0.000", "0.250", 10, null),
            rule(OverlayPolicyType.ARM_CAPS, "0.125", "0.000", "0.250", 10, null),
            rule(OverlayPolicyType.BUYDOWN, "2.500", "0.000", "3.500", 10, null),
            rule(OverlayPolicyType.HIGH_BALANCE, "0.250", "0.125", "0.375", 10, null),
            rule(OverlayPolicyType.ODD_TERM, "-0.125", "-0.250", "0.125", 10, null),
            rule(OverlayPolicyType.JUMBO, "50.000", "25.000", "75.000", 10, null)),
            Map.of("escrowWaived", true, "armCaps", "2/2/6", "buydownType", "2-1", "highBalance", true,
                "jumbo", true, "loanTermMonths", 120));

        assertThat(result.adjustments()).extracting(AdjustmentLine::factorKey)
            .containsExactlyInAnyOrder("ESCROW_WAIVER", "ARM_CAPS", "BUYDOWN", "HIGH_BALANCE", "ODD_TERM");
        assertThat(result.adjustments()).allMatch(line -> "LLPA_ADJUSTMENT".equals(line.outputType()));
    }

    private static AdjustmentCalculationResult calculate(List<OverlayRule> overlays, Map<String, Object> facts) {
        AdjustmentService service = new AdjustmentService(
            new RuleBookResolver(new InMemoryRuleBookRepository(List.of(publishedBook()))),
            new RuleIndexer(),
            new RuleEvaluationEngine(new RuleIndexer(), new PrecisionNormalizer()),
            new InMemoryOverlayRuleRepository(overlays));
        return service.calculate(new AdjustmentCalculationRequest(
            new BasePriceDecisionStub("SCN-OVERLAY-034", "BP-OVERLAY-034", "fixed-rate-30yr", 450000.0, "USD", "pricing-service"),
            Map.of(), List.of(), "rulebook-overlays-v1", TENANT_ID, SELECTOR, QUOTE_DATE, facts));
    }

    private static AdjustmentRuleBook publishedBook() {
        return new AdjustmentRuleBook(TENANT_ID, RULE_BOOK_ID, "llpa-overlay-book", "rulebook-overlays-v1",
            RuleBookStatus.PUBLISHED, SELECTOR,
            new EffectiveWindow(Instant.parse("2026-01-01T00:00:00Z"), null),
            new PricingPrecisionPolicy(6, 4, 2, RoundingMode.HALF_UP), List.of(), "author", "approver",
            Instant.parse("2026-06-01T00:00:00Z"), null);
    }

    private static OverlayRule rule(OverlayPolicyType type, String bps, String min, String max, int priority, String exclusivityGroup) {
        return new OverlayRule(TENANT_ID, RULE_BOOK_ID, UUID.randomUUID(), type, "FNMA", "RETAIL", "CONVENTIONAL",
            "*", "*", "*", "*", "*", new BigDecimal(bps), new BigDecimal(min), new BigDecimal(max),
            priority, exclusivityGroup, "OVERLAY-" + type.name(), Instant.parse("2026-01-01T00:00:00Z"), null, true);
    }

    private static Map<String, Object> facts(String key, Object value) {
        return Map.of(key, value);
    }
}
