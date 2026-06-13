package com.wcpe.ratefeed.cache;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class RateCacheEventHandler {
  private final CachedRateResolver resolver;

  public RateCacheEventHandler(CachedRateResolver resolver) {
    this.resolver = resolver;
  }

  @EventListener
  public void onGridLoaded(GridLoadedEvent event) {
    RateCoverage coverage = event.coverage();
    resolver.invalidateCoverage(coverage);
    precompute(coverage);
  }

  @EventListener
  public void onGridSuperseded(GridSupersededEvent event) {
    resolver.invalidateCoverage(event.coverage());
  }

  void precompute(RateCoverage coverage) {
    CachedRateResolver.COMMON_LOCK_PERIODS.forEach(lockPeriod ->
        resolver.resolve(coverage.tenantId(), coverage.investorId(), coverage.channelId(), coverage.productCode(), lockPeriod, coverage.resolutionTimestamp()));
  }
}
