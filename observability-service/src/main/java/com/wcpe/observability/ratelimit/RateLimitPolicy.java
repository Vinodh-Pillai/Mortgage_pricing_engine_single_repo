package com.wcpe.observability.ratelimit;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record RateLimitPolicy(
    UUID id,
    UUID tenantId,
    String policyKey,
    String endpointGroup,
    PrincipalType principalType,
    String principalHash,
    RateLimitAlgorithm algorithm,
    int limit,
    int burst,
    int emergencyFallbackLimit,
    Duration window,
    RateLimitPolicyStatus status,
    int version,
    Instant effectiveFrom,
    Instant effectiveTo,
    String createdBy,
    String approvedBy) {
  public RateLimitPolicy {
    Objects.requireNonNull(id, "id is required");
    Objects.requireNonNull(tenantId, "tenantId is required");
    policyKey = SafeRateLimitText.requireSafeToken(policyKey, "policyKey", 120);
    endpointGroup = SafeRateLimitText.requireSafeToken(endpointGroup, "endpointGroup", 120);
    Objects.requireNonNull(principalType, "principalType is required");
    if (principalHash != null) {
      principalHash = SafeRateLimitText.requirePrincipalHash(principalHash);
    }
    Objects.requireNonNull(algorithm, "algorithm is required");
    if (limit <= 0 || burst < 0 || emergencyFallbackLimit < 0) {
      throw new IllegalArgumentException("limit must be positive and burst/fallback cap cannot be negative");
    }
    Objects.requireNonNull(window, "window is required");
    if (window.isZero() || window.isNegative()) {
      throw new IllegalArgumentException("window must be positive");
    }
    Objects.requireNonNull(status, "status is required");
    if (version <= 0) {
      throw new IllegalArgumentException("version must be positive");
    }
    Objects.requireNonNull(effectiveFrom, "effectiveFrom is required");
    if (effectiveTo != null && !effectiveTo.isAfter(effectiveFrom)) {
      throw new IllegalArgumentException("effectiveTo must be after effectiveFrom");
    }
    createdBy = SafeRateLimitText.requireSafeToken(createdBy, "createdBy", 128);
    if (status == RateLimitPolicyStatus.PUBLISHED) {
      approvedBy = SafeRateLimitText.requireSafeToken(approvedBy, "approvedBy", 128);
      if (createdBy.equals(approvedBy)) {
        throw new IllegalArgumentException("separation of duties requires creator and approver to differ");
      }
    } else if (approvedBy != null && !approvedBy.isBlank()) {
      approvedBy = SafeRateLimitText.requireSafeToken(approvedBy, "approvedBy", 128);
    }
  }

  int capacity() {
    return limit + burst;
  }

  boolean matches(RateLimitRequest request) {
    return status == RateLimitPolicyStatus.PUBLISHED
        && tenantId.equals(request.tenantId())
        && endpointGroup.equals(request.endpointGroup())
        && principalType == request.principalType()
        && !effectiveFrom.isAfter(request.requestedAt())
        && (effectiveTo == null || request.requestedAt().isBefore(effectiveTo))
        && (principalHash == null || principalHash.equals(request.principalHash()));
  }

  boolean exactPrincipalMatch(RateLimitRequest request) {
    return principalHash != null && principalHash.equals(request.principalHash());
  }
}
