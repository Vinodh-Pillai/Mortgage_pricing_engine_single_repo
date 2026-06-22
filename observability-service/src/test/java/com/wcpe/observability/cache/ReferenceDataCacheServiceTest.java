package com.wcpe.observability.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReferenceDataCacheServiceTest {
  private static final UUID TENANT_ID = UUID.fromString("aaaaaaaa-1111-2222-3333-bbbbbbbbbbbb");
  private static final ReferenceDataset DATASET = new ReferenceDataset("state-rules");
  private static final ReferenceDataVersion VERSION = new ReferenceDataVersion("rd-2026-06");
  private static final LocalDate AS_OF_DATE = LocalDate.parse("2026-06-07");
  private static final Instant NOW = Instant.parse("2026-06-07T17:30:00Z");

  @Test
  void resolvesDeterministicKeyAndCachesOnlyPublishedReferenceData() {
    LocalReferenceDataCacheStore store = new LocalReferenceDataCacheStore();
    ReferenceDataCacheService service = service(store, source(List.of(
        record(ReferenceDataStatus.DRAFT, "draft-digest"),
        record(ReferenceDataStatus.SUSPENDED, "suspended-digest"),
        record(ReferenceDataStatus.PUBLISHED, "sha256-published"))));

    ReferenceCacheSnapshot first = service.resolve(TENANT_ID, DATASET, VERSION, AS_OF_DATE, 1);
    ReferenceCacheSnapshot second = service.resolve(TENANT_ID, DATASET, VERSION, AS_OF_DATE, 1);

    assertEquals(ReferenceCacheStatus.MISS, first.cacheStatus());
    assertEquals(ReferenceCacheStatus.HIT, second.cacheStatus());
    assertEquals("sha256-published", first.payloadDigest());
    assertEquals(
        "wcp:local:tenant:aaaaaaaa-1111-2222-3333-bbbbbbbbbbbb:reference:state-rules:v1:rd-2026-06:2026-06-07",
        first.cacheKey().value());
    assertTrue(first.diagnostics().stream().anyMatch(text -> text.contains("draft/suspended data excluded")));
  }

  @Test
  void failsClosedWhenPublishedReferenceDataIsAmbiguous() {
    ReferenceDataCacheService service = service(new LocalReferenceDataCacheStore(), source(List.of(
        record(ReferenceDataStatus.PUBLISHED, "sha256-a"),
        record(ReferenceDataStatus.PUBLISHED, "sha256-b"))));

    IllegalStateException exception = assertThrows(IllegalStateException.class,
        () -> service.resolve(TENANT_ID, DATASET, VERSION, AS_OF_DATE, 1));

    assertTrue(exception.getMessage().contains("POLICY_NOT_SATISFIED"));
    assertTrue(exception.getMessage().contains("ambiguous"));
  }

  @Test
  void fallsBackToSourceMetadataWhenCacheStoreIsUnavailable() {
    ReferenceDataCacheService service = service(new FailingReferenceDataCacheStore(), source(List.of(
        record(ReferenceDataStatus.PUBLISHED, "sha256-published"))));

    ReferenceCacheSnapshot snapshot = service.resolve(TENANT_ID, DATASET, VERSION, AS_OF_DATE, 1);

    assertEquals(ReferenceCacheStatus.FALLBACK, snapshot.cacheStatus());
    assertEquals("sha256-published", snapshot.payloadDigest());
    assertTrue(snapshot.diagnostics().stream().anyMatch(text -> text.contains("cache read unavailable")));
  }

  @Test
  void buildsInvalidationContractForPublishedSuspendedAndRollbackEvents() {
    ReferenceDataCacheService service = service(new LocalReferenceDataCacheStore(), source(List.of(
        record(ReferenceDataStatus.PUBLISHED, "sha256-published"))));
    ReferenceDataChangeEvent event = new ReferenceDataChangeEvent(
        TENANT_ID,
        DATASET,
        VERSION,
        ReferenceDataChangeType.ROLLED_BACK,
        "corr-PII17S04",
        NOW);

    CacheInvalidationRequestedV1 invalidation = service.invalidationFor(event);

    assertEquals("CacheInvalidationRequested.v1", invalidation.eventType());
    assertEquals("ReferenceDataRolledBack.v1", invalidation.sourceEventType());
    assertEquals("wcp:*:tenant:aaaaaaaa-1111-2222-3333-bbbbbbbbbbbb:reference:state-rules:v*:rd-2026-06:*",
        invalidation.cacheKeyPattern());
  }

  private static ReferenceDataCacheService service(
      ReferenceDataCacheStore store,
      ReferenceDataSource source) {
    return new ReferenceDataCacheService(
        Clock.fixed(NOW, ZoneOffset.UTC),
        new CacheKeyPolicy("local"),
        new CacheTtlPolicy(Map.of(
            ReferenceDataCacheService.REFERENCE_DATA_NAMESPACE,
            new CacheTtl(Duration.ofHours(6)))),
        store,
        source);
  }

  private static ReferenceDataSource source(List<ReferenceDataRecord> records) {
    return (tenantId, dataset, asOfDate) -> records;
  }

  private static ReferenceDataRecord record(ReferenceDataStatus status, String digest) {
    return new ReferenceDataRecord(
        TENANT_ID,
        DATASET,
        VERSION,
        new EffectiveDateWindow(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31")),
        status,
        Instant.parse("2026-06-01T00:00:00Z"),
        digest,
        Map.of("source", "reference-data-service"));
  }

  private static final class FailingReferenceDataCacheStore implements ReferenceDataCacheStore {
    @Override
    public Optional<ReferenceCacheSnapshot> get(CacheKey key) {
      throw new IllegalStateException("redis unavailable");
    }

    @Override
    public void put(CacheKey key, ReferenceCacheSnapshot snapshot) {
      throw new IllegalStateException("redis unavailable");
    }
  }
}
