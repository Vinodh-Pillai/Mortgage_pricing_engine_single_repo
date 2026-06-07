package com.wcpe.mladvisory;

import java.time.Instant;
import java.util.List;

public record AuditSafeExport(
    String explanationId,
    String tenantId,
    String advisoryId,
    boolean authoritative,
    boolean notAdverseAction,
    String disclaimer,
    String summary,
    List<ExplanationDriver> drivers,
    String auditRef,
    String replayHash,
    Instant exportedAt,
    String correlationId) {
  public AuditSafeExport {
    drivers = drivers == null ? List.of() : List.copyOf(drivers);
  }
}
