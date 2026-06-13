package com.wcpe.ratefeed.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.wcpe.ratefeed.resolution.RateResolver;
import com.wcpe.ratefeed.resolution.RateResolver.ResolvedSheet;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class CachedRateResolver {
  public static final Set<Integer> COMMON_LOCK_PERIODS = Set.of(15, 30, 45, 60, 75);

  private final RateResolver dbResolver;
  private final Cache<RateCacheKey, ResolvedSheet> l1Cache;
  private final L2RateCache l2Cache;
  private final MeterRegistry registry;
  private final Timer latencyTimer;
  private final ConcurrentHashMap<RateCacheKey, CompletableFuture<Optional<ResolvedSheet>>> inflight = new ConcurrentHashMap<>();

  public CachedRateResolver(RateResolver dbResolver, Cache<RateCacheKey, ResolvedSheet> l1Cache, L2RateCache l2Cache, MeterRegistry registry) {
    this.dbResolver = dbResolver;
    this.l1Cache = l1Cache;
    this.l2Cache = l2Cache;
    this.registry = registry;
    this.latencyTimer = Timer.builder("rate.cache.lookup.latency")
        .description("Cached rate resolver lookup latency")
        .publishPercentiles(0.5, 0.95, 0.99, 0.999)
        .register(registry);
  }

  public Optional<ResolvedSheet> resolve(UUID tenantId, UUID investorId, UUID channelId, String productCode, int lockPeriod, Instant resolutionTimestamp) {
    return resolve(new RateCacheKey(tenantId, investorId, channelId, productCode, lockPeriod, resolutionTimestamp));
  }

  public Optional<ResolvedSheet> resolve(RateCacheKey key) {
    return latencyTimer.record(() -> resolveUnmetered(key));
  }

  private Optional<ResolvedSheet> resolveUnmetered(RateCacheKey key) {
    ResolvedSheet l1 = l1Cache.getIfPresent(key);
    if (l1 != null) {
      registry.counter("rate.cache.lookup", "tier", "l1", "result", "hit").increment();
      return Optional.of(l1);
    }
    registry.counter("rate.cache.lookup", "tier", "l1", "result", "miss").increment();

    Optional<ResolvedSheet> l2 = l2Cache.get(key);
    if (l2.isPresent()) {
      l1Cache.put(key, l2.get());
      registry.counter("rate.cache.lookup", "tier", "l2", "result", "hit").increment();
      return l2;
    }
    registry.counter("rate.cache.lookup", "tier", "l2", "result", "miss").increment();

    CompletableFuture<Optional<ResolvedSheet>> future = inflight.computeIfAbsent(key, ignored -> CompletableFuture.supplyAsync(() -> {
      try {
        Optional<ResolvedSheet> db = dbResolver.resolve(key.tenantId(), key.investorId(), key.channelId(), key.productCode(), key.lockPeriod(), key.resolutionTimestamp());
        db.ifPresent(sheet -> {
          l1Cache.put(key, sheet);
          l2Cache.put(key, sheet);
        });
        registry.counter("rate.cache.lookup", "tier", "db", "result", db.isPresent() ? "hit" : "miss").increment();
        return db;
      } finally {
        inflight.remove(key);
      }
    }));
    return future.join();
  }

  public void invalidateCoverage(RateCoverage coverage) {
    l1Cache.invalidateAll(l1Cache.asMap().keySet().stream()
        .filter(key -> key.tenantId().equals(coverage.tenantId()))
        .filter(key -> key.investorId().equals(coverage.investorId()))
        .filter(key -> key.channelId().equals(coverage.channelId()))
        .filter(key -> key.productCode().equals(coverage.productCode()))
        .toList());
    l2Cache.invalidateCoverage(coverage);
    registry.counter("rate.cache.invalidation", "reason", "coverage").increment();
  }
}
