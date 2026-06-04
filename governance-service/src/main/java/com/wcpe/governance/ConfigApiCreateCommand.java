package com.wcpe.governance;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record ConfigApiCreateCommand(
    String tenantId,
    String idempotencyKey,
    String actorId,
    String artifactType,
    String displayName,
    String schemaVersion,
    Map<String, String> payload,
    Map<String, String> context,
    Instant effectiveStart,
    Instant effectiveEnd,
    String changeSummary,
    String correlationId) {
  public ConfigApiCreateCommand {
    payload = Map.copyOf(Objects.requireNonNullElse(payload, Map.of()));
    context = Map.copyOf(Objects.requireNonNullElse(context, Map.of()));
  }
}
