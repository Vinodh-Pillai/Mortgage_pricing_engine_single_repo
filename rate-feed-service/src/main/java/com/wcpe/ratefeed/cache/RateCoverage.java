package com.wcpe.ratefeed.cache;

import java.time.Instant;
import java.util.UUID;

public record RateCoverage(UUID tenantId, UUID investorId, UUID channelId, String productCode, Instant resolutionTimestamp) {
  public RateCoverage {
    if (productCode == null || productCode.isBlank()) throw new IllegalArgumentException("productCode is required");
    productCode = productCode.trim().toUpperCase();
    if (resolutionTimestamp == null) resolutionTimestamp = Instant.now();
  }
}
