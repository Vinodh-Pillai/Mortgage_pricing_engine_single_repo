package com.wcpe.observability.cache;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CacheEnvelope(
    int schemaVersion,
    UUID tenantId,
    Instant createdAt,
    Instant expiresAt,
    String producerVersion,
    String payloadHash,
    boolean compressed) {
  public CacheEnvelope {
    if (schemaVersion < 1) {
      throw new IllegalArgumentException("schemaVersion must be positive");
    }
    tenantId = Objects.requireNonNull(tenantId, "tenantId is required");
    createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
    expiresAt = Objects.requireNonNull(expiresAt, "expiresAt is required");
    if (!expiresAt.isAfter(createdAt)) {
      throw new IllegalArgumentException("expiresAt must be after createdAt");
    }
    producerVersion = SafeCacheText.requireSafeToken(producerVersion, "producerVersion", 80);
    payloadHash = SafeCacheText.requireSafeToken(payloadHash, "payloadHash", 128);
  }
}
