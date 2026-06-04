package com.wcpe.mladvisory;

public record MlAdvisoryControlResponse(
    String controlId,
    String tenantId,
    AdvisoryType advisoryType,
    AdvisoryMode configuredMode,
    AdvisoryMode effectiveMode,
    int version,
    boolean killSwitchActive,
    String killSwitchReason,
    String cacheKey,
    String eventRef,
    String auditRef,
    String correlationId) {}
