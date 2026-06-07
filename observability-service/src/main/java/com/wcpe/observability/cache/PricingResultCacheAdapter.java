package com.wcpe.observability.cache;

import com.wcpe.observability.scenariohash.ScenarioHash;
import com.wcpe.observability.scenariohash.ScenarioHashService;
import com.wcpe.observability.scenariohash.VersionGraph;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Supplier;

public final class PricingResultCacheAdapter {
  public static final TenantCacheNamespace PRICING_RESULT_NAMESPACE = new TenantCacheNamespace("pricing-result");

  private final Clock clock;
  private final ScenarioHashService scenarioHashService;
  private final CacheKeyPolicy cacheKeyPolicy;
  private final CacheTtlPolicy ttlPolicy;
  private final PricingResultHasher pricingResultHasher;
  private final PricingResultCacheStore cacheStore;

  public PricingResultCacheAdapter(
      Clock clock,
      ScenarioHashService scenarioHashService,
      CacheKeyPolicy cacheKeyPolicy,
      CacheTtlPolicy ttlPolicy,
      PricingResultHasher pricingResultHasher,
      PricingResultCacheStore cacheStore) {
    this.clock = Objects.requireNonNull(clock, "clock is required");
    this.scenarioHashService = Objects.requireNonNull(scenarioHashService, "scenarioHashService is required");
    this.cacheKeyPolicy = Objects.requireNonNull(cacheKeyPolicy, "cacheKeyPolicy is required");
    this.ttlPolicy = Objects.requireNonNull(ttlPolicy, "ttlPolicy is required");
    this.pricingResultHasher = Objects.requireNonNull(pricingResultHasher, "pricingResultHasher is required");
    this.cacheStore = Objects.requireNonNull(cacheStore, "cacheStore is required");
  }

  public PricingResultCacheOutcome resolve(
      PricingResultCacheRequest request,
      Supplier<Map<String, ?>> pricingComputation) {
    Objects.requireNonNull(request, "request is required");
    Objects.requireNonNull(pricingComputation, "pricingComputation is required");

    Instant now = clock.instant();
    ScenarioHash scenarioHash = scenarioHashService.hash(request.scenario());
    CacheVersion cacheVersion = new CacheVersion(
        request.cacheSchemaVersion(),
        versionRef(request.scenario().versionGraph()));
    CacheKey cacheKey = cacheKeyPolicy.buildKey(
        request.tenantId(),
        PRICING_RESULT_NAMESPACE,
        cacheVersion,
        scenarioHash.value());
    Duration ttl = ttlPolicy.ttlFor(PRICING_RESULT_NAMESPACE).value();

    List<String> diagnostics = new ArrayList<>();
    CacheEligibilityDecision eligibility = request.eligibilityPolicy().evaluate();
    diagnostics.addAll(eligibility.diagnostics());
    if (!eligibility.cacheable()) {
      CachedPricingResult computed = computeResult(request, pricingComputation, scenarioHash, now, ttl);
      return new PricingResultCacheOutcome(
          PricingResultCacheStatus.BYPASS,
          eligibility.bypassReason(),
          cacheKey,
          scenarioHash,
          computed.pricingResultHash(),
          computed.expiresAt(),
          computed,
          diagnostics);
    }

    CachedPricingResult staleResult = null;
    try {
      var cached = cacheStore.get(cacheKey);
      if (cached.isPresent()) {
        CachedPricingResult result = cached.get();
        if (!result.expiredAt(now)) {
          diagnostics.add("cache hit: deterministic key resolved before compute");
          return new PricingResultCacheOutcome(
              PricingResultCacheStatus.HIT,
              null,
              cacheKey,
              scenarioHash,
              result.pricingResultHash(),
              result.expiresAt(),
              result,
              diagnostics);
        }
        staleResult = result;
        diagnostics.add("cache stale: existing entry expired before compute");
      } else {
        diagnostics.add("cache miss: deterministic key absent before compute");
      }
    } catch (RuntimeException exception) {
      diagnostics.add("cache fallback: read path unavailable; pricing computed without Redis dependency");
      CachedPricingResult computed = computeResult(request, pricingComputation, scenarioHash, now, ttl);
      return fallback(cacheKey, scenarioHash, computed, diagnostics);
    }

    CachedPricingResult computed = computeResult(request, pricingComputation, scenarioHash, now, ttl);
    try {
      cacheStore.put(cacheKey, computed);
    } catch (RuntimeException exception) {
      diagnostics.add("cache fallback: write path unavailable; pricing computed without Redis dependency");
      return fallback(cacheKey, scenarioHash, computed, diagnostics);
    }

    return new PricingResultCacheOutcome(
        staleResult == null ? PricingResultCacheStatus.MISS : PricingResultCacheStatus.STALE,
        null,
        cacheKey,
        scenarioHash,
        computed.pricingResultHash(),
        computed.expiresAt(),
        computed,
        diagnostics);
  }

  private PricingResultCacheOutcome fallback(
      CacheKey cacheKey,
      ScenarioHash scenarioHash,
      CachedPricingResult computed,
      List<String> diagnostics) {
    return new PricingResultCacheOutcome(
        PricingResultCacheStatus.FALLBACK,
        null,
        cacheKey,
        scenarioHash,
        computed.pricingResultHash(),
        computed.expiresAt(),
        computed,
        diagnostics);
  }

  private CachedPricingResult computeResult(
      PricingResultCacheRequest request,
      Supplier<Map<String, ?>> pricingComputation,
      ScenarioHash scenarioHash,
      Instant now,
      Duration ttl) {
    Map<String, ?> payload = Objects.requireNonNull(
        pricingComputation.get(),
        "pricingComputation returned null");
    return new CachedPricingResult(
        request.cacheSchemaVersion(),
        request.tenantId(),
        scenarioHash,
        pricingResultHasher.hash(payload),
        sanitizedSummary(payload),
        request.scenario().versionGraph().versions(),
        now,
        now.plus(ttl),
        request.replayRef());
  }

  private static Map<String, String> sanitizedSummary(Map<String, ?> payload) {
    TreeMap<String, String> summary = new TreeMap<>();
    for (Map.Entry<String, ?> entry : payload.entrySet()) {
      String key = SafeCacheText.requireSafeToken(entry.getKey(), "pricing result summary key", 96);
      Object value = entry.getValue();
      if (value == null || value instanceof Map<?, ?> || value instanceof Iterable<?>) {
        continue;
      }
      String text = SafeCacheText.requireSafeToken(value.toString(), "pricing result summary value", 160);
      summary.put(key, text);
    }
    return new LinkedHashMap<>(summary);
  }

  private static String versionRef(VersionGraph versionGraph) {
    Map<String, String> versions = versionGraph.versions();
    String pricingEngineVersion = versions.get("pricingEngineVersion");
    if (pricingEngineVersion != null && !pricingEngineVersion.isBlank()) {
      return SafeCacheText.requireSafeToken(pricingEngineVersion, "pricingEngineVersion", 96);
    }
    return SafeCacheText.requireSafeToken("vg-" + Integer.toHexString(versions.hashCode()), "versionGraphRef", 96);
  }
}
