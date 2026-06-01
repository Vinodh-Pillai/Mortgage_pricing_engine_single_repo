package com.wcpe.ratefeed.activation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * SupersessionEngine: when a new ACTIVE sheet is added for the same
 * (tenant, investor, channel, product), all existing ACTIVE sheets
 * are transitioned to SUPERSEDED.
 */
@Service
public class SupersessionEngine {
  private final JdbcTemplate jdbc;

  public SupersessionEngine(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<UUID> supersede(UUID tenantId, UUID investorId, UUID channelId,
                               String productCode, UUID newSheetId, int newVersion) {
    // Find all ACTIVE sheets for same dimensions, excluding the new sheet
    var ids = jdbc.queryForList(
        "SELECT sheet_id FROM rate_feed.rate_sheet " +
        "WHERE tenant_id = ? AND investor_id = ? AND channel_id = ? " +
        "AND product_code = ? AND status = 'ACTIVE' AND sheet_id != ?",
        UUID.class,
        tenantId, investorId, channelId, productCode, newSheetId);

    if (!ids.isEmpty()) {
      String placeholders = String.join(",",
          Collections.nCopies(ids.size(), "?"));
      List<Object> params = new ArrayList<>(ids);
      // Add tenantId as first param for the UPDATE
      params.add(0, tenantId);
      jdbc.update(
          "UPDATE rate_feed.rate_sheet SET status = 'SUPERSEDED', " +
          "updated_at = now() " +
          "WHERE tenant_id = ? AND sheet_id IN (" + placeholders + ")",
          params.toArray());

      List<Object> lineageParams = new ArrayList<>();
      lineageParams.add(newVersion);
      lineageParams.addAll(ids);
      jdbc.update(
          "UPDATE rate_feed.rate_sheet_version " +
          "SET superseded_by = ? " +
          "WHERE sheet_id IN (" + placeholders + ")",
          lineageParams.toArray());
    }

    return ids;
  }
}
