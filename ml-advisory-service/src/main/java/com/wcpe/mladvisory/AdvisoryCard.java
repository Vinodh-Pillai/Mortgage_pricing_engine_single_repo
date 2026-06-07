package com.wcpe.mladvisory;

import java.time.Instant;
import java.util.List;

public record AdvisoryCard(
    String advisoryId,
    String tenantId,
    String scenarioId,
    String pricingResultId,
    String snapshotId,
    String modelVersionId,
    AdvisoryType advisoryType,
    String recommendation,
    double confidence,
    String confidenceBand,
    boolean authoritative,
    String status,
    Instant generatedAt,
    Instant expiresAt,
    String disclaimer,
    List<AllowedAction> allowedActions,
    List<AdvisoryReason> topReasons,
    boolean collapsedByDefault,
    String panelState,
    String accessibilityLabel,
    String eventRef,
    String auditRef,
    String correlationId) {
  public AdvisoryCard {
    allowedActions = allowedActions == null ? List.of() : List.copyOf(allowedActions);
    topReasons = topReasons == null ? List.of() : List.copyOf(topReasons);
  }
}
