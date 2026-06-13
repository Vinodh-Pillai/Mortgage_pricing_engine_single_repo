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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RuleBookResolverTest {
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000027");
    private static final RuleBookSelector SELECTOR = new RuleBookSelector("CONVENTIONAL", "FNMA", "RETAIL");
    private static final Instant QUOTE_DATE = Instant.parse("2026-06-12T00:00:00Z");

    @Test
    void resolverSelectsHighestPublishedAtMatchingSelector() {
        AdjustmentRuleBook older = book("20000000-0000-0000-0000-000000000001", "v1", Instant.parse("2026-05-01T00:00:00Z"));
        AdjustmentRuleBook newer = book("20000000-0000-0000-0000-000000000002", "v2", Instant.parse("2026-06-01T00:00:00Z"));

        RuleBookResolver resolver = new RuleBookResolver(new RuleBookResolver.InMemoryRuleBookRepository(List.of(older, newer)));

        assertThat(resolver.resolve(TENANT_ID, SELECTOR, QUOTE_DATE)).get().extracting(AdjustmentRuleBook::version).isEqualTo("v2");
    }

    @Test
    void resolverCachesForFiveMinutesAndInvalidatesOnPublish() {
        AtomicInteger calls = new AtomicInteger();
        RuleBookResolver.RuleBookRepository repository = (tenantId, selector, quoteDate) -> {
            calls.incrementAndGet();
            return List.of(book("20000000-0000-0000-0000-000000000003", "v-cache", Instant.parse("2026-06-01T00:00:00Z")));
        };
        RuleBookResolver resolver = new RuleBookResolver(repository, Clock.fixed(QUOTE_DATE, ZoneOffset.UTC), Duration.ofMinutes(5));

        resolver.resolve(TENANT_ID, SELECTOR, QUOTE_DATE);
        resolver.resolve(TENANT_ID, SELECTOR, QUOTE_DATE);
        assertThat(calls).hasValue(1);

        resolver.invalidate(TENANT_ID, SELECTOR);
        resolver.resolve(TENANT_ID, SELECTOR, QUOTE_DATE);
        assertThat(calls).hasValue(2);
    }

    private static AdjustmentRuleBook book(String ruleBookId, String version, Instant publishedAt) {
        AdjustmentRule rule = new AdjustmentRule(UUID.fromString("00000000-0000-0000-0000-000000000901"), 1,
            List.of(new AdjustmentCondition("productFamily", ConditionOperator.EQ, List.of("CONVENTIONAL"))),
            new AdjustmentOutput(AdjustmentOutputType.POINTS_DELTA, new BigDecimal("0.125000"), null),
            "RULE", null, true, "source:RULE");
        return new AdjustmentRuleBook(TENANT_ID, UUID.fromString(ruleBookId), "llpa-real-engine", version, RuleBookStatus.PUBLISHED,
            SELECTOR, new EffectiveWindow(Instant.parse("2026-01-01T00:00:00Z"), null), null, List.of(rule),
            "author", "approver", publishedAt, null);
    }
}
