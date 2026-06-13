package com.wcpe.ratefeed.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.wcpe.ratefeed.resolution.RateResolver;
import com.wcpe.ratefeed.resolution.RateResolver.ResolvedSheet;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RateCacheTest {
  private final UUID tenantId = UUID.randomUUID();
  private final UUID investorId = UUID.randomUUID();
  private final UUID channelId = UUID.randomUUID();
  private final Instant asOf = Instant.parse("2026-06-13T14:00:00Z");
  private final RateCacheKey key = new RateCacheKey(tenantId, investorId, channelId, "CONF30", 30, asOf);
  private final ResolvedSheet sheet = new ResolvedSheet(UUID.randomUUID(), 7, "sha256:grid", 12, "sha256:result");

  @Test
  void l1HitReturnsFast() {
    RateResolver db = mock(RateResolver.class);
    Cache<RateCacheKey, ResolvedSheet> l1 = l1();
    l1.put(key, sheet);
    CachedRateResolver resolver = new CachedRateResolver(db, l1, new MemoryL2(), new SimpleMeterRegistry());

    assertEquals(Optional.of(sheet), resolver.resolve(key));
    verifyNoInteractions(db);
  }

  @Test
  void l1MissL2HitPopulatesL1() {
    RateResolver db = mock(RateResolver.class);
    Cache<RateCacheKey, ResolvedSheet> l1 = l1();
    MemoryL2 l2 = new MemoryL2();
    l2.put(key, sheet);
    CachedRateResolver resolver = new CachedRateResolver(db, l1, l2, new SimpleMeterRegistry());

    assertEquals(Optional.of(sheet), resolver.resolve(key));
    assertEquals(sheet, l1.getIfPresent(key));
    verifyNoInteractions(db);
  }

  @Test
  void l1L2MissDbFallbackPopulatesBoth() {
    RateResolver db = mock(RateResolver.class);
    when(db.resolve(tenantId, investorId, channelId, "CONF30", 30, asOf)).thenReturn(Optional.of(sheet));
    Cache<RateCacheKey, ResolvedSheet> l1 = l1();
    MemoryL2 l2 = new MemoryL2();
    CachedRateResolver resolver = new CachedRateResolver(db, l1, l2, new SimpleMeterRegistry());

    assertEquals(Optional.of(sheet), resolver.resolve(key));
    assertEquals(sheet, l1.getIfPresent(key));
    assertEquals(Optional.of(sheet), l2.get(key));
  }

  @Test
  void invalidationOnGridLoad() {
    RateResolver db = mock(RateResolver.class);
    Cache<RateCacheKey, ResolvedSheet> l1 = l1();
    MemoryL2 l2 = new MemoryL2();
    l1.put(key, sheet);
    l2.put(key, sheet);
    CachedRateResolver resolver = new CachedRateResolver(db, l1, l2, new SimpleMeterRegistry());
    RateCacheEventHandler handler = new RateCacheEventHandler(resolver);

    handler.onGridLoaded(new GridLoadedEvent(tenantId, investorId, channelId, "CONF30", sheet.sheetId(), asOf));

    assertNull(l1.getIfPresent(key));
    assertTrue(l2.get(key).isEmpty());
  }

  @Test
  void preComputationRunsOnGridLoad() {
    RateResolver db = mock(RateResolver.class);
    when(db.resolve(eq(tenantId), eq(investorId), eq(channelId), eq("CONF30"), anyInt(), eq(asOf))).thenReturn(Optional.of(sheet));
    CachedRateResolver resolver = new CachedRateResolver(db, l1(), new MemoryL2(), new SimpleMeterRegistry());
    RateCacheEventHandler handler = new RateCacheEventHandler(resolver);

    handler.onGridLoaded(new GridLoadedEvent(tenantId, investorId, channelId, "CONF30", sheet.sheetId(), asOf));

    for (int lockPeriod : CachedRateResolver.COMMON_LOCK_PERIODS) {
      verify(db).resolve(tenantId, investorId, channelId, "CONF30", lockPeriod, asOf);
    }
  }

  @Test
  void warmUpOnStartup() throws Exception {
    RateResolver db = mock(RateResolver.class);
    when(db.resolve(eq(tenantId), eq(investorId), eq(channelId), eq("CONF30"), anyInt(), eq(asOf))).thenReturn(Optional.of(sheet));
    CachedRateResolver resolver = new CachedRateResolver(db, l1(), new MemoryL2(), new SimpleMeterRegistry());
    var jdbc = mock(org.springframework.jdbc.core.JdbcTemplate.class);
    when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class))).thenReturn(java.util.List.of(new RateCoverage(tenantId, investorId, channelId, "CONF30", asOf)));
    RateCacheProperties properties = new RateCacheProperties();

    new RateCacheWarmer(jdbc, resolver, properties).run(null);

    verify(db, times(CachedRateResolver.COMMON_LOCK_PERIODS.size())).resolve(eq(tenantId), eq(investorId), eq(channelId), eq("CONF30"), anyInt(), eq(asOf));
  }

  private static Cache<RateCacheKey, ResolvedSheet> l1() {
    return Caffeine.newBuilder().maximumSize(10_000).expireAfterWrite(Duration.ofMinutes(5)).recordStats().build();
  }

  private static final class MemoryL2 implements L2RateCache {
    private final Map<RateCacheKey, ResolvedSheet> values = new ConcurrentHashMap<>();
    @Override public Optional<ResolvedSheet> get(RateCacheKey key) { return Optional.ofNullable(values.get(key)); }
    @Override public void put(RateCacheKey key, ResolvedSheet value) { values.put(key, value); }
    @Override public void invalidateCoverage(RateCoverage coverage) {
      values.keySet().removeIf(key -> key.tenantId().equals(coverage.tenantId()) && key.investorId().equals(coverage.investorId())
          && key.channelId().equals(coverage.channelId()) && key.productCode().equals(coverage.productCode()));
    }
  }
}
