package com.wcpe.mladvisory;

public record ResolveControlQuery(
    String tenantId,
    String channel,
    String productFamily,
    AdvisoryType advisoryType,
    String correlationId) {}
