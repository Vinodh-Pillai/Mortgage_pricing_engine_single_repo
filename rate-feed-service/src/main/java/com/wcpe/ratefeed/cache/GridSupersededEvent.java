package com.wcpe.ratefeed.cache;

import java.time.Instant;
import java.util.UUID;

public record GridSupersededEvent(UUID tenantId, UUID investorId, UUID channelId, String productCode, UUID sheetId, Instant effectiveAt) {
  public RateCoverage coverage() { return new RateCoverage(tenantId, investorId, channelId, productCode, effectiveAt); }
}
