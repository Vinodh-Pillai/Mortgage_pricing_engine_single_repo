package com.wcpe.observability.ratelimit;

import java.util.Objects;
import java.util.UUID;

public record RateLimitCounterKey(
    UUID tenantId,
    String policyKey,
    int policyVersion,
    String principalHash,
    String endpointGroup,
    long windowStartEpochSecond) {
  public RateLimitCounterKey {
    Objects.requireNonNull(tenantId, "tenantId is required");
    policyKey = SafeRateLimitText.requireSafeToken(policyKey, "policyKey", 120);
    if (policyVersion <= 0) {
      throw new IllegalArgumentException("policyVersion must be positive");
    }
    principalHash = SafeRateLimitText.requirePrincipalHash(principalHash);
    endpointGroup = SafeRateLimitText.requireSafeToken(endpointGroup, "endpointGroup", 120);
  }
}
