package com.wcpe.observability.backpressure;

public enum BackpressureResource {
  PRICING_CPU("pricing_cpu"),
  DB_POOL("db_pool"),
  REDIS_LATENCY("redis_latency"),
  EVENT_LAG("event_lag"),
  QUEUE_DEPTH("queue_depth"),
  MAINTENANCE("maintenance");

  private final String token;

  BackpressureResource(String token) {
    this.token = token;
  }

  public String token() {
    return token;
  }
}
