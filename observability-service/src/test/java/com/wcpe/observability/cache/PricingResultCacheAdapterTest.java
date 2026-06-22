package com.wcpe.observability.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wcpe.observability.scenariohash.CanonicalPricingScenario;
import com.wcpe.observability.scenariohash.HashSchemaVersion;
import com.wcpe.observability.scenariohash.ScenarioHashService;
import com.wcpe.observability.scenariohash.VersionGraph;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PricingResultCacheAdapterTest {
  private static final UUID TENANT_ID = UUID.fromString("aaaaaaaa-1111-2222-3333-bbbbbbbbbbbb");
  private static final Instant NOW = Instant.parse("2026-06-07T12:00:00Z");

  @Test
  void missWritesDeterministicTenantScenarioResultHashAndTtl() {
    LocalPricingResultCacheStore store = new LocalPricingResultCacheStore();
    PricingResultCacheAdapter adapter = adapter(store, NOW, Duration.ofMinutes(15));

    PricingResultCacheOutcome outcome = adapter.resolve(cacheableRequest(), PricingResultCacheAdapterTest::safePayload);

    assertEquals(PricingResultCacheStatus.MISS, outcome.status());
    assertTrue(outcome.cacheKey().value().startsWith(
        "wcp:local:tenant:aaaaaaaa-1111-2222-3333-bbbbbbbbbbbb:pricing-result:v1:engine-0.1.0:"));
    assertEquals(NOW.plus(Duration.ofMinutes(15)), outcome.expiresAt());
    assertEquals(TENANT_ID, outcome.result().tenantId());
    assertEquals(outcome.scenarioHash(), outcome.result().scenarioHash());
    assertEquals(outcome.pricingResultHash(), outcome.result().pricingResultHash());
    assertEquals("engine-0.1.0", outcome.result().versionGraph().get("pricingEngineVersion"));
    assertFalse(outcome.result().sanitizedResultSummary().toString().toLowerCase().contains("borrower"));
  }

  @Test
  void secondLookupHitsWithoutRecomputing() {
    LocalPricingResultCacheStore store = new LocalPricingResultCacheStore();
    PricingResultCacheAdapter adapter = adapter(store, NOW, Duration.ofMinutes(15));
    AtomicInteger computations = new AtomicInteger();

    adapter.resolve(cacheableRequest(), () -> {
      computations.incrementAndGet();
      return safePayload();
    });
    PricingResultCacheOutcome second = adapter.resolve(cacheableRequest(), () -> {
      computations.incrementAndGet();
      return safePayload();
    });

    assertEquals(PricingResultCacheStatus.HIT, second.status());
    assertEquals(1, computations.get());
  }

  @Test
  void disabledFeatureBypassesStoreAndKeepsPricingAvailable() {
    LocalPricingResultCacheStore store = new LocalPricingResultCacheStore();
    PricingResultCacheAdapter adapter = adapter(store, NOW, Duration.ofMinutes(15));

    PricingResultCacheOutcome outcome = adapter.resolve(request(new CacheEligibilityPolicy(
        false, true, true, false, false, false)), PricingResultCacheAdapterTest::safePayload);

    assertEquals(PricingResultCacheStatus.BYPASS, outcome.status());
    assertEquals(CacheBypassReason.FEATURE_DISABLED, outcome.bypassReason());
    assertTrue(store.get(outcome.cacheKey()).isEmpty());
  }

  @Test
  void staleEntryRecomputesAndReplacesExpiredResult() {
    LocalPricingResultCacheStore store = new LocalPricingResultCacheStore();
    adapter(store, NOW.minus(Duration.ofMinutes(30)), Duration.ofMinutes(10))
        .resolve(cacheableRequest(), PricingResultCacheAdapterTest::safePayload);

    PricingResultCacheOutcome outcome = adapter(store, NOW, Duration.ofMinutes(10))
        .resolve(cacheableRequest(), () -> Map.of(
            "resultRef", "price-summary-002",
            "adjustmentLedgerRef", "ledger-002",
            "replayRef", "replay-002"));

    assertEquals(PricingResultCacheStatus.STALE, outcome.status());
    assertEquals("price-summary-002", outcome.result().sanitizedResultSummary().get("resultRef"));
    assertTrue(outcome.diagnostics().stream().anyMatch(value -> value.contains("cache stale")));
  }

  @Test
  void redisReadFailureFallsBackToComputeWithoutCaching() {
    PricingResultCacheStore failingStore = new PricingResultCacheStore() {
      @Override
      public Optional<CachedPricingResult> get(CacheKey key) {
        throw new IllegalStateException("redis unavailable");
      }

      @Override
      public void put(CacheKey key, CachedPricingResult result) {
        throw new IllegalStateException("redis unavailable");
      }
    };

    PricingResultCacheOutcome outcome = adapter(failingStore, NOW, Duration.ofMinutes(15))
        .resolve(cacheableRequest(), PricingResultCacheAdapterTest::safePayload);

    assertEquals(PricingResultCacheStatus.FALLBACK, outcome.status());
    assertTrue(outcome.diagnostics().stream().anyMatch(value -> value.contains("read path unavailable")));
  }

  @Test
  void unsafePricingResultPayloadIsRejectedBeforeCacheWrite() {
    PricingResultCacheAdapter adapter = adapter(new LocalPricingResultCacheStore(), NOW, Duration.ofMinutes(15));

    assertThrows(IllegalArgumentException.class,
        () -> adapter.resolve(cacheableRequest(), () -> Map.of("borrowerName", "Jane Borrower")));
  }

  private static PricingResultCacheAdapter adapter(
      PricingResultCacheStore store,
      Instant now,
      Duration ttl) {
    return new PricingResultCacheAdapter(
        Clock.fixed(now, ZoneOffset.UTC),
        new ScenarioHashService(),
        new CacheKeyPolicy("local"),
        new CacheTtlPolicy(Map.of(PricingResultCacheAdapter.PRICING_RESULT_NAMESPACE, new CacheTtl(ttl))),
        new PricingResultHasher(),
        store);
  }

  private static PricingResultCacheRequest cacheableRequest() {
    return request(new CacheEligibilityPolicy(true, true, true, false, false, false));
  }

  private static PricingResultCacheRequest request(CacheEligibilityPolicy policy) {
    return new PricingResultCacheRequest(
        TENANT_ID,
        scenario(),
        policy,
        "corr-PII17S03",
        "replay-001",
        1);
  }

  private static CanonicalPricingScenario scenario() {
    return new CanonicalPricingScenario(
        TENANT_ID,
        new HashSchemaVersion(1),
        new VersionGraph(Map.of(
            "pricingEngineVersion", "engine-0.1.0",
            "ruleSetVersion", "rules-2026-06",
            "referenceDataVersion", "ref-2026-06")),
        Map.of(
            "productId", "CONV-30",
            "loan", Map.of(
                "loanAmount", new BigDecimal("425000.00"),
                "borrowerCreditScore", 742)));
  }

  private static Map<String, ?> safePayload() {
    return Map.of(
        "resultRef", "price-summary-001",
        "adjustmentLedgerRef", "ledger-001",
        "replayRef", "replay-001");
  }
}
