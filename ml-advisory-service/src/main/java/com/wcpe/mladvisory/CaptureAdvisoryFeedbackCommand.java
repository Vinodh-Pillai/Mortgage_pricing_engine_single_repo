package com.wcpe.mladvisory;

import java.util.Set;

public record CaptureAdvisoryFeedbackCommand(
    String tenantId,
    String idempotencyKey,
    String actorId,
    Set<String> actorRoles,
    String advisoryId,
    FeedbackOutcome outcome,
    String reasonCode,
    String comment,
    String sourceSurface,
    String supersedesFeedbackId,
    String correlationId) {
  public CaptureAdvisoryFeedbackCommand {
    actorRoles = actorRoles == null ? Set.of() : Set.copyOf(actorRoles);
  }
}
