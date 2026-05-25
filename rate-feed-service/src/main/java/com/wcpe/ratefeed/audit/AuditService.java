package com.wcpe.ratefeed.audit;

import com.wcpe.ratefeed.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * G-008 — structured audit event emission for status transitions.
 */
@Service
public class AuditService {
  private final JdbcTemplate jdbc;

  public AuditService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  /** Emit activation audit event. */
   public void emitActivation(UUID sheetId, int version, String actor, String correlationId,
                               String gridHashBefore, String gridHashAfter, List<UUID> supersededIds) {
    // V-004 fix: separate request audit from response audit
    // Before state
    jdbc.update("insert into rate_feed.audit_event(tenant_id,audit_event_id,event_type,aggregate_type,aggregate_id,actor_id,correlation_id,before_hash,after_hash,payload_redacted,result_hash) values (?,?,?,?,?,?,?,?,?,?,?)",
        tenantFromSheet(sheetId), UUID.randomUUID(), "RATE_SHEET_ACTIVATED", "RateSheet", sheetId,
        actor, correlationId, gridHashBefore, null,
        payload("pre-activation", Map.of("transition", "VALIDATED->ACTIVE")), gridHashAfter);
  }

  /** Emit rejection audit event. */
   public void emitRejection(UUID sheetId, int version, String actor, String correlationId,
                            String gridHash) {
    jdbc.update("insert into rate_feed.audit_event(tenant_id,audit_event_id,event_type,aggregate_type,aggregate_id,actor_id,correlation_id,before_hash,after_hash,payload_redacted,result_hash) values (?,?,?,?,?,?,?,?,?,?,?)",
        tenantFromSheet(sheetId), UUID.randomUUID(), "RATE_SHEET_REJECTED", "RateSheet", sheetId,
        actor, correlationId, gridHash, null, payload("rejection", Map.of()), null);
  }

  /** Emit generic audit event (used for import events). */
  public void emit(UUID sheetId, UUID tenantId, String eventType, String actor, String correlationId, String gridHashBefore, String gridHashAfter, int version) {
    jdbc.update("insert into rate_feed.audit_event(tenant_id,audit_event_id,event_type,aggregate_type,aggregate_id,actor_id,correlation_id,before_hash,after_hash,payload_redacted,result_hash) values (?,?,?,?,?,?,?,?,?,?,?)",
        tenantId, UUID.randomUUID(), eventType, "RateSheet", sheetId,
        actor, correlationId, gridHashBefore, gridHashAfter,
        payload(eventType, Map.of("version", version)), gridHashAfter);
  }

  /** Emit supersession audit event for each superseded sheet. */
   public void emitSupersession(UUID sheetId, UUID supersededSheetId, String actor,
                                  UUID correlationId, String gridHash) {
    jdbc.update("insert into rate_feed.audit_event(tenant_id,audit_event_id,event_type,aggregate_type,aggregate_id,actor_id,correlation_id,before_hash,after_hash,payload_redacted,result_hash) values (?,?,?,?,?,?,?,?,?,?,?)",
        tenantFromSheet(sheetId), UUID.randomUUID(), "RATE_SHEET_SUPERSEDED", "RateSheet",
        supersededSheetId, actor, correlationId, gridHash, null,
        payload("supersession", Map.of("supersededBy", sheetId.toString())), null);
  }

  private UUID tenantFromSheet(UUID sheetId) {
    return jdbc.queryForObject(
        "SELECT tenant_id FROM rate_feed.rate_sheet WHERE sheet_id=?", UUID.class, sheetId);
  }

  private String payload(String phase, Map<String, Object> extra) {
    return "{\"phase\":\"" + phase + "\",\"details\":" + jsonb(extra) + "}";
  }

  private String jsonb(Object obj) {
    try {
      var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
      return mapper.writeValueAsString(obj);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
