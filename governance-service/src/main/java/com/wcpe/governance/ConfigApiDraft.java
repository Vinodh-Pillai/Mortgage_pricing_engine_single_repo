package com.wcpe.governance;

import java.time.Instant;
import java.util.Map;

public record ConfigApiDraft(
    String tenantId,
    String artifactId,
    String artifactType,
    String displayName,
    String versionId,
    int versionNumber,
    String status,
    String schemaVersion,
    Map<String, String> payload,
    Map<String, String> context,
    Instant effectiveStart,
    Instant effectiveEnd,
    String payloadHash,
    String etag,
    String createdBy,
    Instant createdAt,
    String correlationId) {}
