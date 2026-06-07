package com.wcpe.mladvisory;

public record ApproveModelVersionCommand(
    String tenantId,
    String modelVersionId,
    String actorId,
    ModelStatus targetStatus,
    String governanceTicket,
    String reason,
    String correlationId) {}
