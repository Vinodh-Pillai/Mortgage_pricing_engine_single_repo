package com.wcpe.ratefeed.cache;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record RateCacheKey(
    UUID tenantId,
    UUID investorId,
    UUID channelId,
    String productCode,
    int lockPeriod,
    Instant resolutionTimestamp) {

  public RateCacheKey {
    Objects.requireNonNull(tenantId, "tenantId is required");
    Objects.requireNonNull(investorId, "investorId is required");
    Objects.requireNonNull(channelId, "channelId is required");
    Objects.requireNonNull(resolutionTimestamp, "resolutionTimestamp is required");
    if (productCode == null || productCode.isBlank()) {
      throw new IllegalArgumentException("productCode is required");
    }
    productCode = productCode.trim().toUpperCase();
    if (lockPeriod <= 0) {
      throw new IllegalArgumentException("lockPeriod must be greater than zero");
    }
  }

  public String redisKey(String prefix) {
    return prefix + ":" + tenantId + ":" + investorId + ":" + channelId + ":" + productCode + ":" + lockPeriod + ":" + resolutionTimestamp.toEpochMilli();
  }

  public static String redisCoveragePrefix(String prefix, UUID tenantId, UUID investorId, UUID channelId, String productCode) {
    return prefix + ":" + tenantId + ":" + investorId + ":" + channelId + ":" + productCode.trim().toUpperCase() + ":";
  }
}
