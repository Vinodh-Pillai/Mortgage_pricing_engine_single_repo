package com.wcpe.ratefeed.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Cache;
import com.wcpe.ratefeed.resolution.RateResolver;
import com.wcpe.ratefeed.resolution.RateResolver.ResolvedSheet;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class RateCacheBenchmark {
  @Test
  void p99Under5ms() {
    BenchmarkResult result = runL1HitBenchmark();
    assertTrue(result.p99Nanos < Duration.ofMillis(5).toNanos(), "p99 was " + result.p99Nanos + "ns");
  }

  @Test
  void p999Under10ms() {
    BenchmarkResult result = runL1HitBenchmark();
    assertTrue(result.p999Nanos < Duration.ofMillis(10).toNanos(), "p99.9 was " + result.p999Nanos + "ns");
  }

  @Test
  void throughputUnderLoad() {
    BenchmarkResult result = runL1HitBenchmark();
    assertTrue(result.operationsPerSecond > 1_000, "throughput was " + result.operationsPerSecond + " ops/s");
  }

  private static BenchmarkResult runL1HitBenchmark() {
    RateResolver db = mock(RateResolver.class);
    Cache<RateCacheKey, ResolvedSheet> l1 = Caffeine.<RateCacheKey, ResolvedSheet>newBuilder().maximumSize(10_000).expireAfterWrite(Duration.ofMinutes(5)).recordStats().build();
    CachedRateResolver resolver = new CachedRateResolver(db, l1, new L2RateCache() {
      @Override public Optional<ResolvedSheet> get(RateCacheKey key) { return Optional.empty(); }
      @Override public void put(RateCacheKey key, ResolvedSheet value) { }
      @Override public void invalidateCoverage(RateCoverage coverage) { }
    }, new SimpleMeterRegistry());
    RateCacheKey key = new RateCacheKey(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "CONF30", 30, Instant.parse("2026-06-13T14:00:00Z"));
    l1.put(key, new ResolvedSheet(UUID.randomUUID(), 1, "sha256:grid", 1, "sha256:result"));

    int samples = 5_000;
    long[] nanos = new long[samples];
    long startAll = System.nanoTime();
    for (int i = 0; i < samples; i++) {
      long start = System.nanoTime();
      resolver.resolve(key);
      nanos[i] = System.nanoTime() - start;
    }
    long elapsed = System.nanoTime() - startAll;
    Arrays.sort(nanos);
    return new BenchmarkResult(nanos[(int) (samples * 0.99) - 1], nanos[(int) (samples * 0.999) - 1], (long) (samples / (elapsed / 1_000_000_000.0)));
  }

  private record BenchmarkResult(long p99Nanos, long p999Nanos, long operationsPerSecond) {}
}
