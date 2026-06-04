package com.wcpe.governance;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ConfigValidationCommand(
    String tenantId,
    String idempotencyKey,
    String actorId,
    String artifactId,
    String versionId,
    String artifactType,
    String schemaVersion,
    Map<String, String> payload,
    String payloadHash,
    List<ConfigValidationPolicy> policies,
    String validationScope,
    String correlationId) {
  public ConfigValidationCommand {
    payload = Map.copyOf(Objects.requireNonNullElse(payload, Map.of()));
    policies = List.copyOf(Objects.requireNonNullElse(policies, List.of()));
  }
}
