package com.wcpe.auditreplay.domain;

public enum LockReplayStatus {
    REQUESTED,
    RUNNING,
    MATCH,
    MISMATCH,
    FAILED,
    CANCELLED
}
