package com.wcpe.adjustment;

import static org.assertj.core.api.Assertions.assertThat;

import com.wcpe.adjustment.AdjustmentRuleBook.AdjustmentCondition;
import com.wcpe.adjustment.AdjustmentRuleBook.AdjustmentOutput;
import com.wcpe.adjustment.AdjustmentRuleBook.AdjustmentOutputType;
import com.wcpe.adjustment.AdjustmentRuleBook.AdjustmentRule;
import com.wcpe.adjustment.AdjustmentRuleBook.ConditionOperator;
import com.wcpe.adjustment.AdjustmentRuleBook.EffectiveWindow;
import com.wcpe.adjustment.AdjustmentRuleBook.PricingPrecisionPolicy;
import com.wcpe.adjustment.AdjustmentRuleBook.RuleBookSelector;
import com.wcpe.adjustment.AdjustmentRuleBook.RuleBookStatus;
import com.wcpe.adjustment.RuleBookResolver.InMemoryRuleBookRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdjustmentServiceTest {
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000027");
    private static final RuleBookSelector SELECTOR = new RuleBookSelector("CONVENTIONAL", "FNMA", "RETAIL");
    private static final Instant QUOTE_DATE = Instant.parse("2026-06-12T00:00:00Z");

    @Test
    void calculateExecutesResolvedRuleBookInsteadOfMockFactors() {
        AdjustmentRuleBook book = publishedBook(List.of(rule("00000000-0000-0000-0000-000000000101", 1,
            "ficoBandKey", ConditionOperator.RANGE_CLOSED, List.of("740", "759"), AdjustmentOutputType.POINTS_DELTA, "-0.125000", "LLPA-FICO")));
        AdjustmentService service = new AdjustmentService(new RuleBookResolver(new InMemoryRuleBookRepository(List.of(book))));

        AdjustmentCalculationResult result = service.calculate(request(Map.of("ficoBandKey", 745)));

        assertThat(result.calculationMode()).isEqualTo("real-rule-book");
        assertThat(result.adjustments()).hasSize(1);
        assertThat(result.adjustments().get(0).factorKey()).isEqualTo("LLPA-FICO");
        assertThat(result.adjustments().get(0).amount()).isEqualTo(-0.125000d);
        assertThat(result.adjustments().get(0).source()).isEqualTo("rulebook-2026.06.v3");
        assertThat(result.totalAdjustment()).isEqualTo(-0.125000d);
        assertThat(result.referenceDataVersion()).isEqualTo("rulebook-2026.06.v3");
        assertThat(result.auditRefs()).singleElement().asString().contains("rulebook-2026.06.v3").contains("rule:");
        assertThat(result.blocked()).isFalse();
    }

    @Test
    void noPublishedRuleBookReturnsBlockingResult() {
        AdjustmentService service = new AdjustmentService(new RuleBookResolver(new InMemoryRuleBookRepository(List.of())));

        AdjustmentCalculationResult result = service.calculate(request(Map.of("ficoBandKey", 745)));

        assertThat(result.blocked()).isTrue();
        assertThat(result.adjustments()).singleElement().extracting(AdjustmentLine::factorKey).isEqualTo("NO_RULE_BOOK_RESOLVED");
        assertThat(result.calculationMode()).isEqualTo("real-rule-book");
    }

    @Test
    void quoteToAdjustmentToWaterfallHasItemizedReasonCodesAndStableHash() {
        AdjustmentRuleBook book = publishedBook(List.of(
            rule("00000000-0000-0000-0000-000000000102", 1, "ficoBandKey", ConditionOperator.RANGE_CLOSED, List.of("740", "759"), AdjustmentOutputType.POINTS_DELTA, "-0.125000", "LLPA-FICO"),
            rule("00000000-0000-0000-0000-000000000103", 2, "ltv", ConditionOperator.RANGE_CLOSED, List.of("80.01", "85.00"), AdjustmentOutputType.POINTS_DELTA, "0.250000", "LLPA-LTV")
        ));
        AdjustmentService service = new AdjustmentService(new RuleBookResolver(new InMemoryRuleBookRepository(List.of(book))));

        AdjustmentCalculationRequest request = request(Map.of("ficoBandKey", 745, "ltv", new BigDecimal("82.5")));
        AdjustmentCalculationResult first = service.calculate(request);
        AdjustmentCalculationResult second = service.calculate(request);

        assertThat(first.adjustments()).extracting(AdjustmentLine::factorKey).containsExactly("LLPA-FICO", "LLPA-LTV");
        assertThat(first.totalAdjustment()).isEqualTo(0.125000d);
        assertThat(first.resultHash()).isEqualTo(second.resultHash());
    }

    private static AdjustmentCalculationRequest request(Map<String, Object> facts) {
        return new AdjustmentCalculationRequest(
            new BasePriceDecisionStub("SCN-ADJ-027", "BP-027", "fixed-rate-30yr", 450000.0, "USD", "pricing-service"),
            Map.of(),
            List.of(new AdjustmentFactor("ignored-mock-factor", 999.0, "must not be used")),
            "rulebook-2026.06.v3",
            TENANT_ID,
            SELECTOR,
            QUOTE_DATE,
            facts
        );
    }

    private static AdjustmentRuleBook publishedBook(List<AdjustmentRule> rules) {
        return new AdjustmentRuleBook(TENANT_ID, UUID.fromString("20000000-0000-0000-0000-000000000027"), "llpa-real-engine",
            "rulebook-2026.06.v3", RuleBookStatus.PUBLISHED, SELECTOR,
            new EffectiveWindow(Instant.parse("2026-01-01T00:00:00Z"), null),
            new PricingPrecisionPolicy(6, 4, 2, RoundingMode.HALF_UP), rules, "author", "approver",
            Instant.parse("2026-06-01T00:00:00Z"), null);
    }

    private static AdjustmentRule rule(String id, int priority, String dimension, ConditionOperator operator, List<String> values,
                                       AdjustmentOutputType type, String amount, String reason) {
        return new AdjustmentRule(UUID.fromString(id), priority,
            List.of(new AdjustmentCondition(dimension, operator, values)),
            new AdjustmentOutput(type, new BigDecimal(amount), null), reason, null, true, "source-ref:" + reason);
    }
}
