package com.wcpe.governance;

public record ConfigApiDraftResponse(
    String artifactId,
    String versionId,
    int versionNumber,
    String status,
    String etag,
    String payloadHash,
    String auditId,
    String eventId,
    String correlationId) {}
