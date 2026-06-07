package com.wcpe.mladvisory;

public record SuspendModelVersionCommand(
    String tenantId, String modelVersionId, String actorId, String governanceTicket, String reason, String correlationId) {}
