package com.wcpe.mladvisory;

import java.time.Instant;

public record AdvisoryFeedback(
    String feedbackId,
    String tenantId,
    String advisoryId,
    String modelVersionId,
    String snapshotId,
    AdvisoryType advisoryType,
    String confidenceBand,
    String actorId,
    String actorRole,
    String sourceSurface,
    FeedbackOutcome outcome,
    String reasonCode,
    String commentRedacted,
    String commentSensitivity,
    Instant createdAt,
    String supersedesFeedbackId,
    String eventRef,
    String concernEventRef,
    String auditRef,
    String correlationId) {}
