package com.wcpe.ratefeed.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.wcpe.ratefeed.resolution.RateResolver.ResolvedSheet;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@EnableConfigurationProperties(RateCacheProperties.class)
public class RateCacheConfig {

  @Bean
  Cache<RateCacheKey, ResolvedSheet> l1RateCache(RateCacheProperties properties, MeterRegistry registry) {
    Cache<RateCacheKey, ResolvedSheet> cache = Caffeine.newBuilder()
        .maximumSize(properties.getL1MaximumSize())
        .expireAfterWrite(properties.getL1Ttl())
        .recordStats()
        .build();
    registry.gauge("rate.cache.l1.hit_rate", cache, c -> c.stats().hitRate());
    registry.gauge("rate.cache.l1.miss_rate", cache, c -> c.stats().missRate());
    registry.gauge("rate.cache.l1.size", cache, Cache::estimatedSize);
    registry.gauge("rate.cache.l1.eviction_count", cache, c -> c.stats().evictionCount());
    return cache;
  }

  @Bean
  @ConditionalOnBean(StringRedisTemplate.class)
  L2RateCache redisL2RateCache(StringRedisTemplate redis, ObjectMapper mapper, RateCacheProperties properties) {
    return new RedisL2RateCache(redis, mapper, properties);
  }

  @Bean
  @ConditionalOnMissingBean(L2RateCache.class)
  L2RateCache noOpL2RateCache() {
    return new NoOpL2RateCache();
  }
}
