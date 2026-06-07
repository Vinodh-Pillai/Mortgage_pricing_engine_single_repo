package com.wcpe.observability.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CacheInvalidationServiceTest {
  private static final UUID TENANT_ID = UUID.fromString("aaaaaaaa-1111-2222-3333-bbbbbbbbbbbb");
  private static final UUID OTHER_TENANT_ID = UUID.fromString("cccccccc-1111-2222-3333-dddddddddddd");
  private static final Instant NOW = Instant.parse("2026-06-07T18:00:00Z");

  @Test
  void processesTenantScopedSourceEventAndReplaysDuplicateWithoutSideEffects() {
    CacheInvalidationService service = service();
    CacheInvalidationSourceEvent event = sourceEvent("evt-ref-001", 1);

    CacheInvalidationResult first = service.processSourceEvent(event, "idem-PII17S05-001");
    CacheInvalidationResult duplicate = service.processSourceEvent(event, "idem-PII17S05-001");

    assertEquals(CacheInvalidationStatus.SUCCEEDED, first.request().status());
    assertEquals(TENANT_ID, first.request().tenantId());
    assertEquals(new TenantCacheNamespace("pricing-results"), first.request().namespace());
    assertEquals(2, first.events().size());
    assertEquals("CacheInvalidationRequested.v1", first.events().get(0).eventType());
    assertEquals("CacheInvalidationCompleted.v1", first.events().get(1).eventType());
    assertTrue(first.metricNames().contains("cache.invalidation.lag"));
    assertTrue(first.traceNames().contains("cache.invalidation.delete_or_bump"));
    assertEquals(CacheOperation.FLUSH_NAMESPACE, first.auditRecords().get(0).operation());
    assertFalse(first.events().stream()
        .flatMap(envelope -> envelope.payload().values().stream())
        .anyMatch(value -> value.toLowerCase().contains("borrower")));

    assertEquals(CacheInvalidationStatus.REPLAYED, duplicate.request().status());
    assertEquals(0, duplicate.events().size());
  }

  @Test
  void idempotencyAndSourceEventReplayAreTenantScoped() {
    CacheInvalidationService service = service();

    CacheInvalidationResult firstTenant = service.processSourceEvent(
        sourceEvent(TENANT_ID, "evt-ref-shared", 1), "idem-PII17S05-shared");
    CacheInvalidationResult secondTenant = service.processSourceEvent(
        sourceEvent(OTHER_TENANT_ID, "evt-ref-shared", 1), "idem-PII17S05-shared");

    assertEquals(CacheInvalidationStatus.SUCCEEDED, firstTenant.request().status());
    assertEquals(CacheInvalidationStatus.SUCCEEDED, secondTenant.request().status());
    assertEquals(TENANT_ID, firstTenant.request().tenantId());
    assertEquals(OTHER_TENANT_ID, secondTenant.request().tenantId());
    assertEquals(2, firstTenant.events().size());
    assertEquals(2, secondTenant.events().size());
  }

  @Test
  void rejectsIdempotencyKeyConflictWithoutDuplicateInvalidation() {
    CacheInvalidationService service = service();
    service.processSourceEvent(sourceEvent("evt-ref-001", 1), "idem-PII17S05-002");

    IllegalStateException exception = assertThrows(IllegalStateException.class,
        () -> service.processSourceEvent(sourceEvent("evt-ref-002", 1), "idem-PII17S05-002"));

    assertTrue(exception.getMessage().contains("IDEMPOTENCY_CONFLICT"));
  }

  @Test
  void movesUnsupportedSchemaVersionToDeadLetterEvidence() {
    CacheInvalidationService service = service();

    CacheInvalidationResult result = service.processSourceEvent(sourceEvent("evt-ref-003", 2), "idem-PII17S05-003");

    assertEquals(CacheInvalidationStatus.DEAD_LETTERED, result.request().status());
    assertEquals("SCHEMA_VERSION_UNSUPPORTED", result.request().lastErrorCode());
    assertEquals("CacheInvalidationFailed.v1", result.events().get(1).eventType());
    assertTrue(result.metricNames().contains("cache.invalidation.dlq.count"));
  }

  @Test
  void manualInvalidationFailsClosedWithoutOperatorReasonOrPermissionContext() {
    assertThrows(IllegalArgumentException.class, () -> new ManualCacheInvalidationCommand(
        TENANT_ID,
        new TenantCacheNamespace("pricing-results"),
        CacheInvalidationScopeType.TENANT_NAMESPACE,
        "tenant-wide",
        "ops-user-17",
        " ",
        "observability:cache-invalidation-events:write",
        "corr-PII17S05-manual",
        NOW));

    assertThrows(IllegalArgumentException.class, () -> new ManualCacheInvalidationCommand(
        TENANT_ID,
        new TenantCacheNamespace("pricing-results"),
        CacheInvalidationScopeType.TENANT_NAMESPACE,
        "tenant-wide",
        "ops-user-17",
        "config-publication-replay",
        "observability:cache-invalidation-events:read",
        "corr-PII17S05-manual",
        NOW));
  }

  @Test
  void acceptsManualInvalidationWithAuditEvidence() {
    CacheInvalidationService service = service();
    ManualCacheInvalidationCommand command = new ManualCacheInvalidationCommand(
        TENANT_ID,
        new TenantCacheNamespace("pricing-results"),
        CacheInvalidationScopeType.TENANT_NAMESPACE,
        "tenant-wide",
        "ops-user-17",
        "config-publication-replay",
        "observability:cache-invalidation-events:write",
        "corr-PII17S05-manual",
        NOW);

    CacheInvalidationResult result = service.requestManual(command, "idem-PII17S05-004");

    assertEquals(CacheInvalidationStatus.SUCCEEDED, result.request().status());
    assertEquals("config-publication-replay", result.request().operatorReason());
    assertEquals("ops-user-17", result.auditRecords().get(0).requestedBy());
    assertEquals("config-publication-replay", result.auditRecords().get(0).operatorReason());
    assertEquals("config-publication-replay", result.events().get(0).payload().get("operatorReason"));
    assertEquals("TenantCacheFlushRequested.v1", result.request().sourceEventType());
  }

  private static CacheInvalidationService service() {
    return new CacheInvalidationService(
        Clock.fixed(NOW, ZoneOffset.UTC),
        new InMemoryCacheInvalidationRepository());
  }

  private static CacheInvalidationSourceEvent sourceEvent(String eventId, int schemaVersion) {
    return sourceEvent(TENANT_ID, eventId, schemaVersion);
  }

  private static CacheInvalidationSourceEvent sourceEvent(UUID tenantId, String eventId, int schemaVersion) {
    return new CacheInvalidationSourceEvent(
        eventId,
        tenantId,
        "ReferenceDataPublished.v1",
        schemaVersion,
        new TenantCacheNamespace("pricing-results"),
        CacheInvalidationScopeType.REFERENCE_DATA,
        "state-rules",
        "sha256-version-graph-PII17S05",
        "corr-PII17S05",
        "cause-PII17S05",
        "reference-data-service",
        NOW,
        Map.of("referenceVersion", "rd-2026-06"));
  }
}
