package com.wcpe.auditreplay.domain;

public enum QuoteReplayStatus {
    REQUESTED,
    RUNNING,
    MATCH,
    MISMATCH,
    FAILED,
    CANCELLED
}
