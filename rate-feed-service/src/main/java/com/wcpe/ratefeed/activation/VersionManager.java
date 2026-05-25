package com.wcpe.ratefeed.activation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Generates monotonic version numbers scoped to (tenant, investor, channel, product).
 * Version is MAX(version) + 1. First version is 1.
 */
@Service
public class VersionManager {
  private final JdbcTemplate jdbc;

  public VersionManager(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public int nextVersion(UUID tenantId, UUID investorId, UUID channelId,
                          String productCode) {
    return jdbc.queryForObject(
        "SELECT COALESCE(MAX(version), 0) + 1 FROM rate_feed.rate_sheet " +
        "WHERE tenant_id = ? AND investor_id = ? AND channel_id = ? " +
        "AND product_code = ?",
        Integer.class,
        tenantId, investorId, channelId, productCode);
  }
}
