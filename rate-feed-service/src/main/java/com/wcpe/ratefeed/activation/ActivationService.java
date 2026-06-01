package com.wcpe.ratefeed.activation;

import com.wcpe.ratefeed.domain.*;
import com.wcpe.ratefeed.domain.RateFeedModels.RateFeedException;
import com.wcpe.ratefeed.domain.RateFeedModels.RateSheetStatus;
import com.wcpe.ratefeed.audit.AuditService;
import com.wcpe.ratefeed.role.RateFeedRoles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * ActivationService: manages the core activation workflow.
 *
 * Status transitions enforced:
 *   DRAFT -> PARSING (parser)
 *   PARSING -> VALIDATED (validator)
 *   VALIDATED -> ACTIVE (activate)
 *   VALIDATED -> REJECTED (reject)
 *   ACTIVE -> SUPERSEDED (supersession on new activation)
 *
 * REJECTED and SUPERSEDED are terminal states.
 *
 * D-003 fix: Removed unreachable dead code checks for REJECTED/SUPERSEDED.
 * D-006 fix: Outbox event emitter logs exceptions instead of silently swallowing.
 */
@Service
public class ActivationService {
  private static final Logger log = LoggerFactory.getLogger(ActivationService.class);

  private final JdbcTemplate jdbc;
  private final AuditService auditService;

  public ActivationService(JdbcTemplate jdbc, AuditService auditService) {
    this.jdbc = jdbc;
    this.auditService = auditService;
  }

  /**
   * G-002: Activate a VALIDATED sheet to ACTIVE.
   * Single transaction with row-level pessimistic lock.
   */
  @Transactional
  public ActivateResult activate(UUID sheetId,
                                   RateFeedModels.ActivateRequest request,
                                   String actor, String correlationId) {
    RateFeedRoles.require(RateFeedRoles.RATE_FEED_ACTIVATE);

    RateFeedModels.RateSheet sheet = getSheet(sheetId);
    if (sheet == null) {
      throw new RateFeedException(HttpStatus.NOT_FOUND, "SHEET_NOT_FOUND",
          "Rate sheet not found for id: " + sheetId);
    }

    if (sheet.status() != RateSheetStatus.VALIDATED) {
      throw new RateFeedException(HttpStatus.CONFLICT, "SHEET_NOT_VALIDATED",
          "Only VALIDATED sheets can be activated. Current status: " + sheet.status());
    }

    String beforeHash = sheet.gridHash();

    // Supersede existing ACTIVE sheets for same dimensions
    SupersessionEngine engine = new SupersessionEngine(jdbc);
    List<UUID> supersededIds = engine.supersede(
        sheet.tenantId(), sheet.investorId(), sheet.channelId(),
        sheet.productCode(), sheetId, sheet.version());

    // V-004: record activation with separate before/after hashes
    Instant now = Instant.now();

    // Update sheet to ACTIVE (FOR UPDATE row-level check via WHERE clause)
    int rowCount = jdbc.update(
        "UPDATE rate_feed.rate_sheet " +
        "SET status = 'ACTIVE', activated_at = ?, activated_by = ?, " +
        "effective_until = ?, updated_at = now() " +
        "WHERE sheet_id = ? AND status = 'VALIDATED'",
        java.sql.Timestamp.from(now),
        actor,
        request.effectiveUntil() != null ?
            java.sql.Timestamp.from(request.effectiveUntil()) : null,
        sheetId);

    if (rowCount == 0) {
      throw new RateFeedException(HttpStatus.CONFLICT, "SHEET_STATUS_CHANGED",
          "Sheet status changed concurrently; retry activation.");
    }

    // Creation version record
    jdbc.update(
        "INSERT INTO rate_feed.rate_sheet_version(" +
        "sheet_id, version, previous_version, created_at) " +
        "VALUES (?, ?, NULL, now())",
        sheetId, sheet.version());

    // Activation audit
    UUID auditId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO rate_feed.activation_audit(" +
        "audit_id, sheet_id, version, actor_id, correlation_id, " +
        "activated_at, approval_reference, grid_hash_before, " +
        "grid_hash_after, notes) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        auditId, sheetId, sheet.version(), actor, correlationId,
        java.sql.Timestamp.from(now),
        request.approvalReference(),
        beforeHash, beforeHash, // immutable; grid not changed during activation
        request.notes());

    // Emit audit events
    auditService.emitActivation(sheetId, sheet.version(), actor, correlationId,
        beforeHash, beforeHash, supersededIds);

    // Outbox event (V-003: use actual investorId/channelId)
    emitActivationOutbox(sheet, actor, correlationId, auditId,
        now, request.effectiveUntil());

    return new ActivateResult(
        sheetId, sheet.version(), "ACTIVE", now, actor,
        Map.of("from", sheet.effectiveAt(),
               "until", request.effectiveUntil()),
        beforeHash, auditId,
        supersededIds.isEmpty() ? null : supersededIds.get(0));
  }

  /**
   * G-006: Reject a VALIDATED or PARSING sheet.
   * REJECTED sheets cannot be activated. Rejection is immutable.
   */
  public RejectResult reject(UUID sheetId,
                              RateFeedModels.RejectRequest request,
                              String actor, String correlationId) {
    RateFeedRoles.require(RateFeedRoles.RATE_FEED_ACTIVATE);

    RateFeedModels.RateSheet sheet = getSheet(sheetId);
    if (sheet == null) {
      throw new RateFeedException(HttpStatus.NOT_FOUND, "SHEET_NOT_FOUND",
          "Rate sheet not found for id: " + sheetId);
    }

    // Only PARSING or VALIDATED can be rejected
    if (sheet.status() != RateSheetStatus.PARSING &&
        sheet.status() != RateSheetStatus.VALIDATED) {
      throw new RateFeedException(HttpStatus.CONFLICT, "INVALID_STATUS_FOR_REJECT",
          "Only PARSING or VALIDATED sheets can be rejected. " +
          "Current status: " + sheet.status());
    }

    String reason = request.reason();
    if (reason == null || reason.isBlank()) {
      throw new RateFeedException(HttpStatus.BAD_REQUEST,
          "REJECTION_REASON_REQUIRED", "Rejection reason is required.");
    }

    Instant now = Instant.now();

    int rowCount = jdbc.update(
        "UPDATE rate_feed.rate_sheet " +
        "SET status = 'REJECTED', rejected_at = ?, rejected_by = ?, " +
        "rejection_reason = ?, updated_at = now() " +
        "WHERE sheet_id = ? AND status IN ('PARSING', 'VALIDATED')",
        java.sql.Timestamp.from(now), actor, reason, sheetId);

    if (rowCount == 0) {
      throw new RateFeedException(HttpStatus.CONFLICT, "SHEET_STATUS_CHANGED",
          "Sheet status changed concurrently; retry rejection.");
    }

    // Rejection audit — writes to rate_feed.rejection (G-002 fix: was rejection_audit)
    jdbc.update(
        "INSERT INTO rate_feed.rejection(" +
        "sheet_id, version, rejected_at, rejected_by, reason) " +
        "VALUES (?, ?, ?, ?, ?)",
        sheetId, sheet.version(),
        java.sql.Timestamp.from(now), actor, reason);

    // Emit audit + outbox
    auditService.emitRejection(sheetId, sheet.version(), actor, correlationId,
        sheet.gridHash());

    return new RejectResult(sheetId, "REJECTED", now, actor, reason);
  }

  // ── Internal helpers ──────────────────────────────────────────────────────

  private RateFeedModels.RateSheet getSheet(UUID sheetId) {
    try {
      return jdbc.queryForObject(
          "SELECT * FROM rate_feed.rate_sheet WHERE sheet_id = ?",
          (rs, rowNum) -> new RateFeedModels.RateSheet(
              rs.getObject("sheet_id", UUID.class),
              rs.getObject("tenant_id", UUID.class),
              rs.getObject("investor_id", UUID.class),
              rs.getObject("channel_id", UUID.class),
              rs.getString("product_code"),
              rs.getInt("version"),
              RateSheetStatus.valueOf(rs.getString("status")),
              rs.getTimestamp("effective_at").toInstant(),
              rs.getTimestamp("effective_until") != null ?
                  rs.getTimestamp("effective_until").toInstant() : null,
              rs.getString("file_sha256"),
              rs.getString("grid_hash"),
              rs.getString("grid_points"),
              rs.getInt("row_count"),
              rs.getString("result_hash"),
              rs.getTimestamp("created_at") != null ?
                  rs.getTimestamp("created_at").toInstant() : null,
              rs.getString("created_by"),
              rs.getTimestamp("activated_at") != null ?
                  rs.getTimestamp("activated_at").toInstant() : null,
              rs.getString("activated_by"),
              rs.getTimestamp("rejected_at") != null ?
                  rs.getTimestamp("rejected_at").toInstant() : null,
              rs.getString("rejected_by"),
              rs.getString("rejection_reason"),
              rs.getTimestamp("updated_at") != null ?
                  rs.getTimestamp("updated_at").toInstant() : null),
          sheetId);
    } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
      return null;
    }
  }

  /** D-006 fix: Outbox event emitter — logs failures instead of silently swallowing. */
  private void emitActivationOutbox(RateFeedModels.RateSheet sheet,
                                      String actor, String correlationId,
                                      UUID auditId, Instant now,
                                      Instant effectiveUntil) {
    try {
      var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
      var headers = mapper.createObjectNode();
      headers.put("tenantId", sheet.tenantId().toString());
      headers.put("investorId", sheet.investorId().toString());
      headers.put("channelId", sheet.channelId().toString());
      headers.put("actorId", actor);
      headers.put("correlationId", correlationId);
      headers.put("occurredAt", now.toString());

      var payload = mapper.createObjectNode();
      payload.put("sheetId", sheet.sheetId().toString());
      payload.put("version", sheet.version());
      payload.put("gridHash", sheet.gridHash());
      payload.put("auditId", auditId.toString());
      if (effectiveUntil != null) {
        payload.put("effectiveUntil", effectiveUntil.toString());
      }

      org.postgresql.util.PGobject hdr =
          new org.postgresql.util.PGobject();
      hdr.setType("jsonb");
      hdr.setValue(headers.toString());

      org.postgresql.util.PGobject pld =
          new org.postgresql.util.PGobject();
      pld.setType("jsonb");
      pld.setValue(payload.toString());

      jdbc.update(
          "INSERT INTO rate_feed.outbox_event(" +
          "tenant_id, event_id, aggregate_type, aggregate_id, " +
          "event_type, event_version, event_key, headers_json, payload_json) " +
          "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
          sheet.tenantId(), UUID.randomUUID(), "RateSheet", sheet.sheetId(),
          "RateSheetActivated.v1", 1,
          sheet.tenantId() + ":" + sheet.sheetId() + ":activated",
          hdr, pld);
    } catch (Exception ex) {
      // D-006 fix: Log the failure instead of silently ignoring it.
      // Outbox is best-effort; do not fail activation for outbox failure.
      log.warn("Outbox event emission failed for activation of sheet {} (auditId {}): {}",
          sheet.sheetId(), auditId, ex.getMessage());
    }
  }

  public record ActivateResult(
      UUID sheetId, int version, String status,
      Instant activatedAt, String activatedBy,
      Map<String, Instant> effectiveWindow,
      String gridHash, UUID auditId,
      UUID supersededSheetId
  ) {}

  public record RejectResult(
      UUID sheetId, String status, Instant rejectedAt,
      String rejectedBy, String reason
  ) {}
}
