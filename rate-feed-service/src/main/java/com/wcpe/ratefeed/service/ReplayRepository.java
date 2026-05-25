package com.wcpe.ratefeed.service;

import com.wcpe.ratefeed.domain.RateFeedModels;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/**
 * ReplayRepository: repository methods for replay operations.
 */
@Repository
public class ReplayRepository {
  private final JdbcTemplate jdbc;

  public ReplayRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public RateFeedModels.RateSheet findEffectiveSheetByVersionAndDate(UUID investorId, UUID channelId,
      String productCode, int version, Instant asOfDate) {
    try {
      return jdbc.queryForObject(
          "SELECT * FROM rate_feed.rate_sheet WHERE investor_id = ? AND channel_id = ? " +
          "AND product_code = ? AND version = ? AND effective_at <= ? " +
          "AND (effective_until IS NULL OR effective_until > ?) ORDER BY version DESC LIMIT 1",
          (rs, row) -> new RateFeedModels.RateSheet(
              rs.getObject("sheet_id", UUID.class),
              rs.getObject("tenant_id", UUID.class),
              rs.getObject("investor_id", UUID.class),
              rs.getObject("channel_id", UUID.class),
              rs.getString("product_code"),
              rs.getInt("version"),
              RateFeedModels.RateSheetStatus.valueOf(rs.getString("status")),
              rs.getTimestamp("effective_at").toInstant(),
              rs.getTimestamp("effective_until") != null ? rs.getTimestamp("effective_until").toInstant() : null,
              rs.getString("file_sha256"),
              rs.getString("grid_hash"),
              rs.getString("grid_points"),
              rs.getInt("row_count"),
              rs.getString("result_hash"),
              rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : null,
              rs.getString("created_by"),
              rs.getTimestamp("activated_at") != null ? rs.getTimestamp("activated_at").toInstant() : null,
              rs.getString("activated_by"),
              rs.getTimestamp("rejected_at") != null ? rs.getTimestamp("rejected_at").toInstant() : null,
              rs.getString("rejected_by"),
              rs.getString("rejection_reason"),
              rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toInstant() : null),
          investorId, channelId, productCode, version,
          Timestamp.from(asOfDate), Timestamp.from(asOfDate));
    } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
      return null;
    }
  }

  public List<com.wcpe.ratefeed.domain.RatePricePoint> findPricePoints(UUID sheetId) {
    return jdbc.query(
        "SELECT * FROM rate_feed.rate_price_point WHERE sheet_id = ? ORDER BY note_rate, lock_period",
        (rs, row) -> new com.wcpe.ratefeed.domain.RateFeedModels.RatePricePoint(
            rs.getObject("sheet_id", UUID.class),
            rs.getBigDecimal("note_rate"),
            rs.getInt("lock_period"),
            rs.getBigDecimal("base_price"),
            rs.getBigDecimal("discount_points"),
            rs.getBigDecimal("yield_index"),
            rs.getInt("grid_position")),
        sheetId);
  }

  public UUID saveReplay(UUID sheetId, int version, String inputHash, String outputHash,
      String replayedBy, String correlationId) {
    UUID replayId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO rate_feed.replay_record(replay_id, sheet_id, version, input_hash, output_hash, replayed_by, correlation_id) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?)",
        replayId, sheetId, version, inputHash, outputHash, replayedBy, correlationId);
    return replayId;
  }
}
