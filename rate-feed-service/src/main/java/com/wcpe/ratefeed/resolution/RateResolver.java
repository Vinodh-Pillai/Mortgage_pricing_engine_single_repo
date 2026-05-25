package com.wcpe.ratefeed.resolution;

import com.wcpe.ratefeed.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * G-003 — resolves highest-version ACTIVE sheet in effective window.
 */
@Service
public class RateResolver {
  private final JdbcTemplate jdbc;

  public RateResolver(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  /**
   * Returns the highest-version ACTIVE sheet within effective window
   * that has at least one price point matching the lockPeriod.
   */
  public Optional<ResolvedSheet> resolve(UUID tenantId, UUID investorId, UUID channelId,
                                        String productCode, int lockPeriod, Instant resolutionTimestamp) {
    String sql = "SELECT rs.*, rs.row_count as point_count FROM rate_feed.rate_sheet rs " +
        "WHERE rs.tenant_id = ? AND rs.investor_id = ? AND rs.channel_id = ? " +
        "AND rs.product_code = ? AND rs.status = 'ACTIVE' " +
        "AND rs.effective_at <= ?::timestamptz " +
        "AND (rs.effective_until IS NULL OR rs.effective_until > ?::timestamptz) " +
        "AND EXISTS (SELECT 1 FROM rate_feed.rate_price_point rpp WHERE rpp.sheet_id = rs.sheet_id AND rpp.lock_period = ?) " +
        "ORDER BY rs.version DESC LIMIT 1";

    List<ResolvedSheet> results = jdbc.query(sql, (rs, row) ->
        new ResolvedSheet(
            rs.getObject("sheet_id", UUID.class),
            rs.getInt("version"),
            rs.getString("grid_hash"),
            rs.getInt("point_count"),
            rs.getString("result_hash")
        ), tenantId, investorId, channelId, productCode,
            java.sql.Timestamp.from(resolutionTimestamp),
            java.sql.Timestamp.from(resolutionTimestamp),
            lockPeriod);

    return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
  }

  public record ResolvedSheet(
      UUID sheetId, int version, String gridHash, int pointCount, String resultHash
  ) {}
}
