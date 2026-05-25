package com.wcpe.ratefeed.audit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Transition-specific event payload emission.
 * Writes structured entries to activation_audit for activation-specific events.
 */
@Service
public class AuditEventEmitter {
  private final JdbcTemplate jdbc;

  public AuditEventEmitter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  /**
   * Emit a structured transition event to outbox.
   */
   public void emit(UUID tenantId, UUID sheetId, String eventType, UUID correlationId,
                   String actorId, String gridHashBefore, String gridHashAfter, int version) {
    Map<String, Object> payload = Map.of(
        "sheetId", sheetId.toString(),
        "version", version,
        "eventTimestamp", Instant.now().toString()
    );
    if (gridHashBefore != null) payload.put("gridHashBefore", gridHashBefore);
    if (gridHashAfter != null) payload.put("gridHashAfter", gridHashAfter);

    // V-003 fix: use actual session values, not tenantId placeholder
    Map<String, Object> headers = new LinkedHashMap<>();
    headers.put("tenantId", tenantId);
    headers.put("eventType", eventType);
    headers.put("sourceService", "rate-feed-service");
    headers.put("actorId", actorId);
    headers.put("correlationId", correlationId);
    headers.put("occurredAt", Instant.now().toString());

    jdbc.update("insert into rate_feed.outbox_event(tenant_id,event_id,aggregate_type,aggregate_id,event_type,event_version,event_key,headers,payload) values (?,?,?,?,?,?,?,?,?)",
        tenantId, UUID.randomUUID(), "RateSheet", sheetId.toString(), eventType, 1,
        sheetId + ":" + eventType, jsonb(headers), jsonb(payload));
  }

  private String jsonb(Object value) {
    try {
      var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
      return mapper.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }
}
