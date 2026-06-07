package com.wcpe.observability.ratelimit;

public enum RateLimitDecision {
  ALLOWED,
  THROTTLED,
  FAIL_OPEN_DEGRADED,
  FAIL_CLOSED
}
