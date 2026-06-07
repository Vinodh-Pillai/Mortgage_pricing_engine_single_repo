package com.wcpe.observability.backpressure;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record BackpressureStateSnapshot(
    UUID tenantId,
    BackpressureResource resource,
    BackpressureState state,
    int policyVersion,
    String triggerMetric,
    BigDecimal triggerValue,
    Instant startedAt,
    Instant updatedAt,
    Instant expiresAt,
    String correlationId) {
  public BackpressureStateSnapshot {
    Objects.requireNonNull(tenantId, "tenantId is required");
    Objects.requireNonNull(resource, "resource is required");
    Objects.requireNonNull(state, "state is required");
    triggerMetric = SafeBackpressureText.requireSafeToken(triggerMetric, "triggerMetric", 120);
    Objects.requireNonNull(triggerValue, "triggerValue is required");
    Objects.requireNonNull(startedAt, "startedAt is required");
    Objects.requireNonNull(updatedAt, "updatedAt is required");
    correlationId = SafeBackpressureText.requireSafeToken(correlationId, "correlationId", 128);
  }
}
