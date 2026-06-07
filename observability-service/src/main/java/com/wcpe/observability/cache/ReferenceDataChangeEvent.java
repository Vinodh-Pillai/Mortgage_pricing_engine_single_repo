package com.wcpe.observability.cache;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ReferenceDataChangeEvent(
    UUID tenantId,
    ReferenceDataset dataset,
    ReferenceDataVersion version,
    ReferenceDataChangeType changeType,
    String correlationId,
    Instant occurredAt) {
  public ReferenceDataChangeEvent {
    tenantId = Objects.requireNonNull(tenantId, "tenantId is required");
    dataset = Objects.requireNonNull(dataset, "dataset is required");
    version = Objects.requireNonNull(version, "version is required");
    changeType = Objects.requireNonNull(changeType, "changeType is required");
    correlationId = SafeCacheText.requireSafeToken(correlationId, "correlationId", 80);
    occurredAt = Objects.requireNonNull(occurredAt, "occurredAt is required");
  }

  public String sourceEventType() {
    return changeType.eventType();
  }
}
