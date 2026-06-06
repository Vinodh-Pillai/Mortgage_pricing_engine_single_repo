package com.wcpe.auditreplay.domain;

public enum OutboxEventStatus {
    PENDING,
    IN_FLIGHT,
    PUBLISHED,
    FAILED,
    POISON
}
