package com.wcpe.observability.loadtest;

public enum PricingLoadTestProfile {
  SMOKE("smoke", "1 virtual user contract check"),
  BASELINE("baseline", "target load for 15 minutes"),
  STRESS("stress", "ramp until latency breach or backpressure is observed"),
  DEGRADATION("degradation", "Redis unavailable while DB remains available");

  private final String code;
  private final String purpose;

  PricingLoadTestProfile(String code, String purpose) {
    this.code = code;
    this.purpose = purpose;
  }

  public String code() {
    return code;
  }

  public String purpose() {
    return purpose;
  }
}
