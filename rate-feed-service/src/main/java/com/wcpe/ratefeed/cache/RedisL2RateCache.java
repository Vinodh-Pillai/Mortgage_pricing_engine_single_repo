package com.wcpe.ratefeed.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.ratefeed.resolution.RateResolver.ResolvedSheet;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

final class RedisL2RateCache implements L2RateCache {
  private static final Logger log = LoggerFactory.getLogger(RedisL2RateCache.class);
  private final StringRedisTemplate redis;
  private final ObjectMapper mapper;
  private final RateCacheProperties properties;

  RedisL2RateCache(StringRedisTemplate redis, ObjectMapper mapper, RateCacheProperties properties) {
    this.redis = redis;
    this.mapper = mapper.findAndRegisterModules();
    this.properties = properties;
  }

  @Override
  public Optional<ResolvedSheet> get(RateCacheKey key) {
    try {
      String value = redis.opsForValue().get(key.redisKey(properties.getRedisKeyPrefix()));
      return value == null ? Optional.empty() : Optional.of(mapper.readValue(value, ResolvedSheet.class));
    } catch (Exception ex) {
      log.warn("Redis L2 rate cache read unavailable; falling back to L1/DB", ex);
      return Optional.empty();
    }
  }

  @Override
  public void put(RateCacheKey key, ResolvedSheet value) {
    try {
      redis.opsForValue().set(key.redisKey(properties.getRedisKeyPrefix()), mapper.writeValueAsString(value), properties.getL2Ttl());
    } catch (Exception ex) {
      log.warn("Redis L2 rate cache write unavailable; continuing with L1/DB", ex);
    }
  }

  @Override
  public void invalidateCoverage(RateCoverage coverage) {
    String prefix = RateCacheKey.redisCoveragePrefix(properties.getRedisKeyPrefix(), coverage.tenantId(), coverage.investorId(), coverage.channelId(), coverage.productCode());
    try {
      redis.execute((org.springframework.data.redis.core.RedisCallback<Void>) connection -> {
        try (var cursor = connection.scan(ScanOptions.scanOptions().match(prefix + "*").count(500).build())) {
          while (cursor.hasNext()) connection.keyCommands().del(cursor.next());
        }
        return null;
      });
    } catch (RuntimeException ex) {
      log.warn("Redis L2 rate cache invalidation unavailable for prefix {}; continuing with L1 invalidation", prefix, ex);
    }
  }
}
