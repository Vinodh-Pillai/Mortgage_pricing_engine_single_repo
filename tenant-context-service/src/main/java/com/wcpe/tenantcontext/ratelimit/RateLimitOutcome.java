package com.wcpe.tenantcontext.ratelimit;

public enum RateLimitOutcome {
    ALLOWED,
    THROTTLED,
    MONITOR_ONLY,
    DISABLED,
    MISSING_POLICY_FAIL_CLOSED
}
