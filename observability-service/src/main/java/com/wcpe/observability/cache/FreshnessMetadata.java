package com.wcpe.observability.cache;

public record FreshnessMetadata(
    FreshnessSource source,
    String description,
    Long observedAgeMillis) {
  public FreshnessMetadata {
    source = source == null ? FreshnessSource.ABSENT : source;
    description = description == null ? "" : description.strip();
    if (observedAgeMillis != null && observedAgeMillis < 0) {
      throw new IllegalArgumentException("observedAgeMillis must not be negative");
    }
  }

  public static FreshnessMetadata absent() {
    return new FreshnessMetadata(FreshnessSource.ABSENT, "freshness metadata absent", null);
  }
}
