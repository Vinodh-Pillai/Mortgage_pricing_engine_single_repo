package com.wcpe.mladvisory;

public record ModelVersionResponse(
    String modelVersionId,
    String tenantId,
    String modelName,
    String semanticVersion,
    ModelStatus status,
    AllowedUse allowedUse,
    int version,
    String eventRef,
    String auditRef,
    String cacheInvalidationRef,
    String correlationId) {}
