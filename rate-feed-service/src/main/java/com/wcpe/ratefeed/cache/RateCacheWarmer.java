package com.wcpe.ratefeed.cache;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class RateCacheWarmer implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(RateCacheWarmer.class);
  private final JdbcTemplate jdbc;
  private final CachedRateResolver resolver;
  private final RateCacheProperties properties;

  public RateCacheWarmer(JdbcTemplate jdbc, CachedRateResolver resolver, RateCacheProperties properties) {
    this.jdbc = jdbc;
    this.resolver = resolver;
    this.properties = properties;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!properties.isWarmupEnabled()) return;
    try {
      activeCoverages().forEach(coverage -> CachedRateResolver.COMMON_LOCK_PERIODS.forEach(lockPeriod ->
          resolver.resolve(coverage.tenantId(), coverage.investorId(), coverage.channelId(), coverage.productCode(), lockPeriod, coverage.resolutionTimestamp())));
    } catch (DataAccessException ex) {
      log.warn("Rate cache warm-up skipped because active rate sheets are unavailable", ex);
    }
  }

  List<RateCoverage> activeCoverages() {
    return jdbc.query("select tenant_id, investor_id, channel_id, product_code, effective_at from rate_feed.rate_sheet where status = 'ACTIVE'",
        (rs, row) -> new RateCoverage(
            rs.getObject("tenant_id", UUID.class),
            rs.getObject("investor_id", UUID.class),
            rs.getObject("channel_id", UUID.class),
            rs.getString("product_code"),
            timestampOrNow(rs.getTimestamp("effective_at"))));
  }

  private static Instant timestampOrNow(Timestamp timestamp) {
    return timestamp == null ? Instant.now() : timestamp.toInstant();
  }
}
