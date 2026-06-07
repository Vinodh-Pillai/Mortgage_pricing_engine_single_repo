package com.wcpe.observability.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CacheSerializationTest {
  private static final UUID TENANT_ID = UUID.fromString("aaaaaaaa-1111-2222-3333-bbbbbbbbbbbb");

  @Test
  void serializesDeterministicTenantScopedEnvelopeWithoutPayloadContent() {
    CacheEnvelope envelope = new CacheEnvelope(
        1,
        TENANT_ID,
        Instant.parse("2026-06-07T14:45:00Z"),
        Instant.parse("2026-06-07T15:00:00Z"),
        "observability-service-0.1.0",
        "sha256-deadbeef",
        false);

    assertEquals(
        "{\"schemaVersion\":1,\"tenantId\":\"aaaaaaaa-1111-2222-3333-bbbbbbbbbbbb\",\"createdAt\":\"2026-06-07T14:45:00Z\",\"expiresAt\":\"2026-06-07T15:00:00Z\",\"producerVersion\":\"observability-service-0.1.0\",\"payloadHash\":\"sha256-deadbeef\",\"compressed\":false}",
        new CacheEnvelopeSerializer().toJson(envelope));
  }

  @Test
  void rejectsEnvelopeWhenExpiryDoesNotFollowCreation() {
    assertThrows(IllegalArgumentException.class, () -> new CacheEnvelope(
        1,
        TENANT_ID,
        Instant.parse("2026-06-07T15:00:00Z"),
        Instant.parse("2026-06-07T15:00:00Z"),
        "observability-service-0.1.0",
        "sha256-deadbeef",
        false));
  }

  @Test
  void rejectsSensitivePayloadHashMetadata() {
    assertThrows(IllegalArgumentException.class, () -> new CacheEnvelope(
        1,
        TENANT_ID,
        Instant.parse("2026-06-07T14:45:00Z"),
        Instant.parse("2026-06-07T15:00:00Z"),
        "observability-service-0.1.0",
        "customer-token-secret",
        false));
  }
}
