package com.wcpe.adjustment;

import static org.assertj.core.api.Assertions.assertThat;

import com.wcpe.adjustment.AdjustmentRuleBook.EffectiveWindow;
import com.wcpe.adjustment.AdjustmentRuleBook.RuleBookSelector;
import com.wcpe.adjustment.AdjustmentRuleBook.RuleBookStatus;
import com.wcpe.adjustment.RuleBookPublishedEventHandler.RuleBookPublishedEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RuleBookPublishedEventHandlerTest {
  @Test
  void ruleBookPublishedInvalidatesResolverCache() {
    UUID tenantId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    RuleBookSelector selector = new RuleBookSelector("CONVENTIONAL", "FNMA", "RETAIL");
    AtomicInteger repositoryCalls = new AtomicInteger();
    AdjustmentRuleBook book = new AdjustmentRuleBook(tenantId, UUID.fromString("20000000-0000-0000-0000-000000003201"), "FNMA_LLPA_2026_06", "2026.06", RuleBookStatus.PUBLISHED,
        selector, new EffectiveWindow(Instant.parse("2026-06-01T00:00:00Z"), null), null, List.of(rule()), "analyst", "approver", Instant.parse("2026-06-02T00:00:00Z"), null);
    RuleBookResolver resolver = new RuleBookResolver((requestedTenant, requestedSelector, quoteDate) -> {
      repositoryCalls.incrementAndGet();
      return List.of(book);
    }, java.time.Clock.fixed(Instant.parse("2026-06-03T00:00:00Z"), java.time.ZoneOffset.UTC), Duration.ofMinutes(5));
    resolver.resolve(tenantId, selector, Instant.parse("2026-06-04T00:00:00Z"));
    resolver.resolve(tenantId, selector, Instant.parse("2026-06-04T00:00:00Z"));
    assertThat(repositoryCalls).hasValue(1);

    RuleBookPublishedEventHandler handler = new RuleBookPublishedEventHandler(resolver);
    var receipt = handler.handle(new RuleBookPublishedEvent(tenantId, book.ruleBookId(), book.businessKey(), selector, book.publishedAt(), "RuleBookPublished.v1"));
    resolver.resolve(tenantId, selector, Instant.parse("2026-06-04T00:00:00Z"));

    assertThat(receipt.status()).isEqualTo("INVALIDATED");
    assertThat(repositoryCalls).hasValue(2);
  }

  private static AdjustmentRuleBook.AdjustmentRule rule() {
    return new AdjustmentRuleBook.AdjustmentRule(UUID.fromString("30000000-0000-0000-0000-000000003201"), 1,
        List.of(new AdjustmentRuleBook.AdjustmentCondition("ficoBandKey", AdjustmentRuleBook.ConditionOperator.EQ, List.of("720-739"))),
        new AdjustmentRuleBook.AdjustmentOutput(AdjustmentRuleBook.AdjustmentOutputType.BPS_DELTA, java.math.BigDecimal.ONE, null),
        "FNMA_LLPA", "FNMA_LLPA_CORE", true, "test");
  }
}
