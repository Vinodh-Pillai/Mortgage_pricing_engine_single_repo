package com.wcpe.ratefeed.audit;

import java.util.*;

public final class AuditEnvelopeRedaction {
  public static final String ELEVATED_PERMISSION = "RATE_FEED_AUDIT_EXPORT";
  private static final String REDACTED = "[REDACTED]";
  private static final Set<String> SENSITIVE_FIELDS = Set.of(
      "basePrice",
      "discountPoints",
      "yieldIndex",
      "rawRate",
      "rawPricePoints",
      "actualPrice");

  private AuditEnvelopeRedaction() {}

  public static Map<String, Object> redact(Map<String, Object> payload, Set<String> roles) {
    if (payload == null || payload.isEmpty()) return Map.of();
    if ("ELEVATED".equals(getRedactionLevel(roles))) return new LinkedHashMap<>(payload);

    Map<String, Object> redacted = new LinkedHashMap<>(payload);
    for (String field : SENSITIVE_FIELDS) {
      if (redacted.containsKey(field)) redacted.put(field, REDACTED);
    }
    return redacted;
  }

  public static String getRedactionLevel(Set<String> roles) {
    return roles != null && roles.contains(ELEVATED_PERMISSION) ? "ELEVATED" : "STANDARD";
  }
}
