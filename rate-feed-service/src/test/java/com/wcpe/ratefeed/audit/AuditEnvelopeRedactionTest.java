package com.wcpe.ratefeed.audit;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AuditEnvelopeRedactionTest {

  @Test
  void redaction_standard_removes_sensitive_fields() {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("basePrice", "101.25");
    payload.put("discountPoints", "0.125");
    payload.put("yieldIndex", "7.1");
    payload.put("rawRate", "6.500");
    payload.put("rawPricePoints", "99.875");
    payload.put("actualPrice", "100.125");
    payload.put("safeField", "visible");

    Map<String, Object> redacted = AuditEnvelopeRedaction.redact(payload, Set.of("RATE_FEED_AUDIT_VIEW"));

    assertEquals("[REDACTED]", redacted.get("basePrice"));
    assertEquals("[REDACTED]", redacted.get("discountPoints"));
    assertEquals("[REDACTED]", redacted.get("yieldIndex"));
    assertEquals("[REDACTED]", redacted.get("rawRate"));
    assertEquals("[REDACTED]", redacted.get("rawPricePoints"));
    assertEquals("[REDACTED]", redacted.get("actualPrice"));
    assertEquals("visible", redacted.get("safeField"));
  }

  @Test
  void redaction_elevated_keeps_sensitive_fields() {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("basePrice", "101.25");
    payload.put("discountPoints", "0.125");
    payload.put("yieldIndex", "7.1");
    payload.put("rawRate", "6.500");
    payload.put("rawPricePoints", "99.875");
    payload.put("actualPrice", "100.125");

    Map<String, Object> redacted = AuditEnvelopeRedaction.redact(payload, Set.of("RATE_FEED_AUDIT_EXPORT"));

    assertEquals(payload, redacted);
    assertNotSame(payload, redacted, "Elevated redaction should preserve values without returning the mutable input map");
  }

  @Test
  void redaction_null_payload_returns_empty() {
    assertTrue(AuditEnvelopeRedaction.redact(null, Set.of("RATE_FEED_AUDIT_VIEW")).isEmpty());
  }

  @Test
  void redaction_empty_payload_returns_empty() {
    assertTrue(AuditEnvelopeRedaction.redact(Map.of(), Set.of("RATE_FEED_AUDIT_VIEW")).isEmpty());
  }

  @Test
  void redaction_level_standard_default() {
    assertEquals("STANDARD", AuditEnvelopeRedaction.getRedactionLevel(Set.of("RATE_FEED_AUDIT_VIEW")));
    assertEquals("STANDARD", AuditEnvelopeRedaction.getRedactionLevel(null));
  }

  @Test
  void redaction_level_elevated() {
    assertEquals("ELEVATED", AuditEnvelopeRedaction.getRedactionLevel(Set.of("RATE_FEED_AUDIT_EXPORT")));
  }
}
