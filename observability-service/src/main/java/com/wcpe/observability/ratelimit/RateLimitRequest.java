package com.wcpe.observability.ratelimit;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record RateLimitRequest(
    UUID tenantId,
    String principalHash,
    PrincipalType principalType,
    String endpointGroup,
    String correlationId,
    EndpointRisk endpointRisk,
    Instant requestedAt) {
  public RateLimitRequest {
    Objects.requireNonNull(tenantId, "tenantId is required");
    principalHash = SafeRateLimitText.requirePrincipalHash(principalHash);
    Objects.requireNonNull(principalType, "principalType is required");
    endpointGroup = SafeRateLimitText.requireSafeToken(endpointGroup, "endpointGroup", 120);
    correlationId = SafeRateLimitText.requireSafeToken(correlationId, "correlationId", 128);
    Objects.requireNonNull(endpointRisk, "endpointRisk is required");
    Objects.requireNonNull(requestedAt, "requestedAt is required");
  }
}
