package com.wcpe.governance;

import java.util.Map;

public record ConfigValidationFinding(
    String findingId,
    String runId,
    ConfigValidationSeverity severity,
    String code,
    String jsonPath,
    String artifactRef,
    String messageKey,
    Map<String, String> messageParams,
    String remediation,
    boolean blocking,
    int sortOrder) {}
