package com.wcpe.adjustment;

import com.wcpe.adjustment.AdjustmentRuleBook.RuleBookSelector;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.event.EventListener;

/** Invalidates resolved-rule-book cache entries when governance publishes a new adjustment rule book. */
public final class RuleBookPublishedEventHandler {
  private final RuleBookResolver resolver;
  private final AdjustmentService adjustmentService;

  public RuleBookPublishedEventHandler(RuleBookResolver resolver) {
    this(resolver, null);
  }

  public RuleBookPublishedEventHandler(RuleBookResolver resolver, AdjustmentService adjustmentService) {
    this.resolver = Objects.requireNonNull(resolver, "resolver is required");
    this.adjustmentService = adjustmentService;
  }

  @EventListener
  public CacheInvalidationReceipt handle(RuleBookPublishedEvent event) {
    Objects.requireNonNull(event, "event is required");
    Objects.requireNonNull(event.tenantId(), "tenantId is required");
    Objects.requireNonNull(event.selector(), "selector is required");
    resolver.invalidate(event.tenantId(), event.selector());
    Optional.ofNullable(adjustmentService).ifPresent(service -> service.invalidateRuleBook(event.tenantId(), event.selector()));
    return new CacheInvalidationReceipt(event.ruleBookId(), event.tenantId(), event.selector(), "INVALIDATED", Instant.now());
  }

  public record RuleBookPublishedEvent(UUID tenantId, UUID ruleBookId, String businessKey, RuleBookSelector selector, Instant publishedAt, String eventType) {}
  public record CacheInvalidationReceipt(UUID ruleBookId, UUID tenantId, RuleBookSelector selector, String status, Instant invalidatedAt) {}
}
