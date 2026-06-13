package com.wcpe.adjustment;

import static org.assertj.core.api.Assertions.assertThat;

import com.wcpe.adjustment.AdjustmentRuleBook.AdjustmentCondition;
import com.wcpe.adjustment.AdjustmentRuleBook.AdjustmentOutput;
import com.wcpe.adjustment.AdjustmentRuleBook.AdjustmentOutputType;
import com.wcpe.adjustment.AdjustmentRuleBook.AdjustmentRule;
import com.wcpe.adjustment.AdjustmentRuleBook.ConditionOperator;
import com.wcpe.adjustment.AdjustmentRuleBook.EffectiveWindow;
import com.wcpe.adjustment.AdjustmentRuleBook.RuleBookSelector;
import com.wcpe.adjustment.AdjustmentRuleBook.RuleBookStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RuleIndexerTest {
    @Test
    void dimensionIndexBuilt() {
        AdjustmentRule fico = rule("00000000-0000-0000-0000-000000000111", "ficoBandKey", "FICO");
        AdjustmentRule ltv = rule("00000000-0000-0000-0000-000000000112", "ltvBandKey", "LTV");

        RuleIndexer.RuleBookIndex index = new RuleIndexer().index(book(List.of(fico, ltv)));

        assertThat(index.rules()).containsKeys(fico.ruleId(), ltv.ruleId());
        assertThat(index.dimensionIndex()).containsKeys("ficoBandKey", "ltvBandKey");
        assertThat(index.dimensionIndex().get("ficoBandKey")).containsExactly(fico.ruleId());
        assertThat(index.dimensionIndex().get("ltvBandKey")).containsExactly(ltv.ruleId());
    }

    private static AdjustmentRuleBook book(List<AdjustmentRule> rules) {
        return new AdjustmentRuleBook(UUID.fromString("10000000-0000-0000-0000-000000000033"),
            UUID.fromString("20000000-0000-0000-0000-000000000033"), "llpa-real-engine", "rulebook-2026.06.v3",
            RuleBookStatus.PUBLISHED, new RuleBookSelector("CONVENTIONAL", "FNMA", "RETAIL"),
            new EffectiveWindow(Instant.parse("2026-01-01T00:00:00Z"), null), null, rules,
            "author", "approver", Instant.parse("2026-06-01T00:00:00Z"), null);
    }

    private static AdjustmentRule rule(String id, String dimension, String reason) {
        return new AdjustmentRule(UUID.fromString(id), 1,
            List.of(new AdjustmentCondition(dimension, ConditionOperator.EQ, List.of(reason))),
            new AdjustmentOutput(AdjustmentOutputType.POINTS_DELTA, new BigDecimal("0.125000"), null),
            reason, null, true, "source:" + reason);
    }
}
