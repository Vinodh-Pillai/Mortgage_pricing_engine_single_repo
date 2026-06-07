package com.wcpe.observability.backpressure;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record BackpressureRequest(
    UUID tenantId,
    BackpressureResource resource,
    BackpressurePriorityClass priorityClass,
    String endpointGroup,
    String actorId,
    String correlationId,
    boolean asyncDeferralSupported,
    Instant requestedAt) {
  public BackpressureRequest {
    Objects.requireNonNull(tenantId, "tenantId is required");
    Objects.requireNonNull(resource, "resource is required");
    Objects.requireNonNull(priorityClass, "priorityClass is required");
    endpointGroup = SafeBackpressureText.requireSafeToken(endpointGroup, "endpointGroup", 120);
    actorId = SafeBackpressureText.requireSafeToken(actorId, "actorId", 128);
    correlationId = SafeBackpressureText.requireSafeToken(correlationId, "correlationId", 128);
    Objects.requireNonNull(requestedAt, "requestedAt is required");
  }
}
