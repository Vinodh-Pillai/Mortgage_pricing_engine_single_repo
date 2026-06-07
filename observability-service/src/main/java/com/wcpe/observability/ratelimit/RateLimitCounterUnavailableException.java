package com.wcpe.observability.ratelimit;

public final class RateLimitCounterUnavailableException extends RuntimeException {
  public RateLimitCounterUnavailableException(String message) {
    super(message);
  }
}
