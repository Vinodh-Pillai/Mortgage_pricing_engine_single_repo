package com.wcpe.tenantcontext.health;

public enum ReadinessComponent {
    POSTGRESQL,
    REDIS,
    BROKER,
    OUTBOX,
    CONSUMERS,
    AUDIT,
    RATE_LIMITS,
    OBSERVABILITY
}
