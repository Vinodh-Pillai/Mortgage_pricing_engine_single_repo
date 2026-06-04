package com.wcpe.mladvisory;

import java.time.Instant;

public record MlAdvisoryControl(
    String id,
    String tenantId,
    String channel,
    String productFamily,
    AdvisoryType advisoryType,
    AdvisoryMode mode,
    Instant effectiveFrom,
    Instant effectiveTo,
    int version,
    String status,
    String createdBy,
    Instant createdAt,
    String approvedBy,
    String approvalRef,
    String changeReason,
    String modelRiskTicket) {}
