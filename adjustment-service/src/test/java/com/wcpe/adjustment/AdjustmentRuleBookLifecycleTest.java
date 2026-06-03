package com.wcpe.adjustment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wcpe.adjustment.AdjustmentRuleBook.AdjustmentCondition;
import com.wcpe.adjustment.AdjustmentRuleBook.AdjustmentOutput;
import com.wcpe.adjustment.AdjustmentRuleBook.AdjustmentOutputType;
import com.wcpe.adjustment.AdjustmentRuleBook.AdjustmentRule;
import com.wcpe.adjustment.AdjustmentRuleBook.ConditionOperator;
import com.wcpe.adjustment.AdjustmentRuleBook.EffectiveWindow;
import com.wcpe.adjustment.AdjustmentRuleBook.PricingPrecisionPolicy;
import com.wcpe.adjustment.AdjustmentRuleBook.RuleBookResolutionService;
import com.wcpe.adjustment.AdjustmentRuleBook.RuleBookSelector;
import com.wcpe.adjustment.AdjustmentRuleBook.RuleBookStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdjustmentRuleBookLifecycleTest {
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final RuleBookSelector SELECTOR = new RuleBookSelector("CONVENTIONAL", "CONFIGURED_INVESTOR", "RETAIL");
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void draftRuleBookValidatesConfigurableRulesWithoutHardcodedLlpaValues() {
        AdjustmentRuleBook book = draftRuleBook(List.of(validRule("ficoBandKey", "FICO_BAND_CONFIG_REF")));

        assertThat(book.validateRules()).isEmpty();
        assertThat(book.contentHash()).hasSize(64);
        assertThat(book.rules().get(0).output().configuredAmount()).isEqualByComparingTo("0.125000");
    }

    @Test
    void unknownConditionDimensionsFailClosed() {
        AdjustmentRuleBook book = draftRuleBook(List.of(validRule("borrowerName", "not-allowed")));

        assertThatThrownBy(book::validateRules)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("unknown condition dimension: borrowerName");
    }

    @Test
    void rulesRequireConditionOutputReasonPriorityAndSourceReference() {
        AdjustmentRule invalid = new AdjustmentRule(
            UUID.randomUUID(),
            -1,
            List.of(),
            new AdjustmentOutput(AdjustmentOutputType.LABEL_ONLY, null, "configured-label"),
            "CONFIGURED_REASON",
            null,
            true,
            "source-doc-ref"
        );
        AdjustmentRuleBook book = draftRuleBook(List.of(invalid));

        assertThatThrownBy(book::validateRules)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("priority must be non-negative")
            .hasMessageContaining("requires at least one condition");
    }

    @Test
    void publishingRequiresSeparationOfDutiesAndNoPublishedOverlap() {
        AdjustmentRuleBook book = draftRuleBook(List.of(validRule("channel", "RETAIL")));

        assertThatThrownBy(() -> book.publish("requester-1", Instant.parse("2026-01-02T00:00:00Z"), List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("submitter cannot publish");

        AdjustmentRuleBook published = book.publish("approver-1", Instant.parse("2026-01-02T00:00:00Z"), List.of());

        assertThat(published.status()).isEqualTo(RuleBookStatus.PUBLISHED);
        assertThat(published.approvedBy()).isEqualTo("approver-1");
        assertThatThrownBy(() -> book.publish("approver-2", Instant.parse("2026-01-03T00:00:00Z"), List.of(published)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("overlaps existing selector");
    }

    @Test
    void publishedRuleBooksAreImmutable() {
        AdjustmentRuleBook published = draftRuleBook(List.of(validRule("state", "CONFIGURED_STATE_REF")))
            .publish("approver-1", Instant.parse("2026-01-02T00:00:00Z"), List.of());

        assertThatThrownBy(() -> published.replaceDraftRules(List.of(validRule("county", "CONFIGURED_COUNTY_REF"))))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("immutable");
    }

    @Test
    void resolverSelectsOnlyMatchingPublishedEffectiveRuleBook() {
        AdjustmentRuleBook older = draftRuleBook(
            UUID.fromString("20000000-0000-0000-0000-000000000001"),
            new EffectiveWindow(START, Instant.parse("2026-06-01T00:00:00Z")),
            List.of(validRule("productFamily", "CONVENTIONAL"))
        ).publish("approver-1", Instant.parse("2026-01-02T00:00:00Z"), List.of());
        AdjustmentRuleBook newer = draftRuleBook(
            UUID.fromString("20000000-0000-0000-0000-000000000002"),
            new EffectiveWindow(Instant.parse("2026-06-01T00:00:00Z"), null),
            List.of(validRule("productFamily", "CONVENTIONAL"))
        ).publish("approver-1", Instant.parse("2026-06-02T00:00:00Z"), List.of(older));

        AdjustmentRuleBook resolved = new RuleBookResolutionService().resolvePublished(
            TENANT_ID,
            SELECTOR,
            Instant.parse("2026-07-01T00:00:00Z"),
            List.of(older, newer)
        );

        assertThat(resolved.ruleBookId()).isEqualTo(newer.ruleBookId());
        assertThatThrownBy(() -> new RuleBookResolutionService().resolvePublished(
            TENANT_ID,
            new RuleBookSelector("CONVENTIONAL", "CONFIGURED_INVESTOR", "WHOLESALE"),
            Instant.parse("2026-07-01T00:00:00Z"),
            List.of(older, newer)
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("no published rule book resolves");
    }

    private static AdjustmentRuleBook draftRuleBook(List<AdjustmentRule> rules) {
        return draftRuleBook(UUID.fromString("20000000-0000-0000-0000-000000000000"), new EffectiveWindow(START, null), rules);
    }

    private static AdjustmentRuleBook draftRuleBook(UUID ruleBookId, EffectiveWindow window, List<AdjustmentRule> rules) {
        return new AdjustmentRuleBook(
            TENANT_ID,
            ruleBookId,
            "configured-business-key",
            "v1",
            RuleBookStatus.DRAFT,
            SELECTOR,
            window,
            new PricingPrecisionPolicy(6, 4, 2, java.math.RoundingMode.HALF_UP),
            rules,
            "requester-1",
            null,
            null,
            null
        );
    }

    private static AdjustmentRule validRule(String dimension, String configuredValue) {
        return new AdjustmentRule(
            UUID.randomUUID(),
            1,
            List.of(new AdjustmentCondition(dimension, ConditionOperator.EQ, List.of(configuredValue))),
            new AdjustmentOutput(AdjustmentOutputType.POINTS_DELTA, new BigDecimal("0.125000"), null),
            "CONFIGURED_REASON",
            "configured-exclusivity-group",
            true,
            "source-doc-ref"
        );
    }
}
