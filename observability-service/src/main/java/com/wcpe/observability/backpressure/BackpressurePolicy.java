package com.wcpe.observability.backpressure;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record BackpressurePolicy(
    UUID policyId,
    UUID tenantId,
    BackpressureResource resource,
    int version,
    BackpressurePolicyStatus status,
    Instant effectiveFrom,
    Instant effectiveTo,
    List<BackpressureTrigger> triggers,
    int recoveryWindows,
    Duration minimumStateDuration,
    Duration retryAfter,
    String createdBy,
    String approvedBy) {
  public BackpressurePolicy {
    Objects.requireNonNull(policyId, "policyId is required");
    Objects.requireNonNull(tenantId, "tenantId is required");
    Objects.requireNonNull(resource, "resource is required");
    Objects.requireNonNull(status, "status is required");
    Objects.requireNonNull(effectiveFrom, "effectiveFrom is required");
    Objects.requireNonNull(minimumStateDuration, "minimumStateDuration is required");
    Objects.requireNonNull(retryAfter, "retryAfter is required");
    createdBy = SafeBackpressureText.requireSafeToken(createdBy, "createdBy", 128);
    if (approvedBy != null) {
      approvedBy = SafeBackpressureText.requireSafeToken(approvedBy, "approvedBy", 128);
    }
    triggers = List.copyOf(Objects.requireNonNull(triggers, "triggers are required"));
    if (version <= 0 || recoveryWindows <= 0 || minimumStateDuration.isNegative() || retryAfter.isNegative()) {
      throw new IllegalArgumentException("version, recovery, duration, and retryAfter must be configured");
    }
    if (status == BackpressurePolicyStatus.PUBLISHED && (approvedBy == null || approvedBy.equals(createdBy))) {
      throw new IllegalArgumentException("published backpressure policy requires approval separation of duties");
    }
    if (status == BackpressurePolicyStatus.PUBLISHED && triggers.isEmpty()) {
      throw new IllegalArgumentException("published backpressure policy requires tenant-governed triggers");
    }
  }

  public boolean appliesTo(UUID requestTenantId, BackpressureResource requestResource, Instant now) {
    return tenantId.equals(requestTenantId)
        && resource == requestResource
        && status == BackpressurePolicyStatus.PUBLISHED
        && !effectiveFrom.isAfter(now)
        && (effectiveTo == null || effectiveTo.isAfter(now));
  }
}
