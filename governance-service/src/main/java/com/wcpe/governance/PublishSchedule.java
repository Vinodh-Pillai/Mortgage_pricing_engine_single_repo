package com.wcpe.governance;

import java.time.Instant;

public record PublishSchedule(
    String scheduleId,
    String tenantId,
    String artifactId,
    String versionId,
    Instant requestedEffectiveStart,
    Instant requestedEffectiveEnd,
    String status,
    String scheduledBy,
    Instant scheduledAt,
    Instant executedAt) {}
