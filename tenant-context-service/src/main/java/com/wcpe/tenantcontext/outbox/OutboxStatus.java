package com.wcpe.tenantcontext.outbox;

public enum OutboxStatus {
    PENDING,
    PUBLISHING,
    PUBLISHED,
    FAILED,
    RETRY_WAIT,
    DLQ,
    QUARANTINED
}
