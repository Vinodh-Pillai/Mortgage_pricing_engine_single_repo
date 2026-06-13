package com.wcpe.ratefeed.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.ratefeed.activation.ActivationService;
import com.wcpe.ratefeed.activation.VersionManager;
import com.wcpe.ratefeed.audit.AuditService;
import com.wcpe.ratefeed.cache.CachedRateResolver;
import com.wcpe.ratefeed.cache.L2RateCache;
import com.wcpe.ratefeed.cache.RateCacheKey;
import com.wcpe.ratefeed.cache.RateCoverage;
import com.wcpe.ratefeed.resolution.GridLookup;
import com.wcpe.ratefeed.resolution.RateResolver;
import com.wcpe.ratefeed.service.ReplayRepository;
import com.wcpe.ratefeed.service.ReplayService;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Cache;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.Optional;

public final class TestRateFeedServices {
  private TestRateFeedServices() {}

  public static RateFeedService create(RateFeedRepository repository) {
    JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
    return create(repository, jdbc);
  }

  public static RateFeedService create(RateFeedRepository repository, JdbcTemplate jdbc) {
    AuditService auditService = new AuditService(jdbc);
    Cache<RateCacheKey, RateResolver.ResolvedSheet> l1 = Caffeine.<RateCacheKey, RateResolver.ResolvedSheet>newBuilder().maximumSize(10_000).expireAfterWrite(Duration.ofMinutes(5)).recordStats().build();
    return new RateFeedService(
        repository,
        jdbc,
        new ObjectMapper(),
        new ActivationService(jdbc, auditService),
        new VersionManager(jdbc),
        new CachedRateResolver(new RateResolver(jdbc), l1, new L2RateCache() {
          @Override public Optional<RateResolver.ResolvedSheet> get(RateCacheKey key) { return Optional.empty(); }
          @Override public void put(RateCacheKey key, RateResolver.ResolvedSheet value) { }
          @Override public void invalidateCoverage(RateCoverage coverage) { }
        }, new SimpleMeterRegistry()),
        new GridLookup(jdbc),
        auditService,
        new ReplayService(jdbc, new ReplayRepository(jdbc)),
        event -> { });
  }
}
