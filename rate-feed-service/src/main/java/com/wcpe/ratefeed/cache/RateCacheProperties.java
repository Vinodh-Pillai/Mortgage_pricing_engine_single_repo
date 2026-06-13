package com.wcpe.ratefeed.cache;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ratefeed.cache")
public class RateCacheProperties {
  private int l1MaximumSize = 10_000;
  private Duration l1Ttl = Duration.ofMinutes(5);
  private Duration l2Ttl = Duration.ofHours(1);
  private String redisKeyPrefix = "rate:v1";
  private boolean warmupEnabled = true;

  public int getL1MaximumSize() { return l1MaximumSize; }
  public void setL1MaximumSize(int l1MaximumSize) { this.l1MaximumSize = l1MaximumSize; }
  public Duration getL1Ttl() { return l1Ttl; }
  public void setL1Ttl(Duration l1Ttl) { this.l1Ttl = l1Ttl; }
  public Duration getL2Ttl() { return l2Ttl; }
  public void setL2Ttl(Duration l2Ttl) { this.l2Ttl = l2Ttl; }
  public String getRedisKeyPrefix() { return redisKeyPrefix; }
  public void setRedisKeyPrefix(String redisKeyPrefix) { this.redisKeyPrefix = redisKeyPrefix; }
  public boolean isWarmupEnabled() { return warmupEnabled; }
  public void setWarmupEnabled(boolean warmupEnabled) { this.warmupEnabled = warmupEnabled; }
}
