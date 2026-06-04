package com.wcpe.governance;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ConfigValidationPolicy(
    String policyId,
    String version,
    List<String> requiredPayloadFields,
    List<String> supportedSchemaVersions,
    Map<String, String> messageKeys) {
  public ConfigValidationPolicy {
    requiredPayloadFields = List.copyOf(Objects.requireNonNullElse(requiredPayloadFields, List.of()));
    supportedSchemaVersions = List.copyOf(Objects.requireNonNullElse(supportedSchemaVersions, List.of()));
    messageKeys = Map.copyOf(Objects.requireNonNullElse(messageKeys, Map.of()));
  }
}
