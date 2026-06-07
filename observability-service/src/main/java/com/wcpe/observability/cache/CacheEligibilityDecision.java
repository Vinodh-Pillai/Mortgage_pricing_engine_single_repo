package com.wcpe.observability.cache;

import java.util.List;

public record CacheEligibilityDecision(
    boolean cacheable,
    CacheBypassReason bypassReason,
    List<String> diagnostics) {
  public CacheEligibilityDecision {
    diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    if (!cacheable && bypassReason == null) {
      throw new IllegalArgumentException("bypassReason is required when cacheable is false");
    }
    if (cacheable && bypassReason != null) {
      throw new IllegalArgumentException("bypassReason must be absent when cacheable is true");
    }
  }

  public static CacheEligibilityDecision cacheAllowed() {
    return new CacheEligibilityDecision(true, null, List.of("pricing result cache eligible"));
  }

  public static CacheEligibilityDecision bypass(CacheBypassReason reason) {
    return new CacheEligibilityDecision(false, reason, List.of(reason.name()));
  }
}
