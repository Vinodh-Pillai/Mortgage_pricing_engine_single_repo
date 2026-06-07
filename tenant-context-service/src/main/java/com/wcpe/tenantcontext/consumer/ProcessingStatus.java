package com.wcpe.tenantcontext.consumer;

public enum ProcessingStatus {
    RECEIVED,
    PROCESSING,
    PROCESSED,
    FAILED,
    RETRY_WAIT,
    DLQ,
    QUARANTINED
}
