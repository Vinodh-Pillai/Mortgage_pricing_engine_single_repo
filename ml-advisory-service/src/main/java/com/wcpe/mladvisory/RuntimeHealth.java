package com.wcpe.mladvisory;

import java.time.Instant;

public record RuntimeHealth(
    String runtimeId,
    String modelVersionId,
    String artifactChecksum,
    Instant loadedAt,
    String status,
    String lastError,
    Instant updatedAt,
    boolean killSwitchEnabled) {}
