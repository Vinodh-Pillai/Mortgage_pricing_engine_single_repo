package com.wcpe.observability.cache;

public record ReferenceDataVersion(String value) {
  public ReferenceDataVersion {
    value = SafeCacheText.requireSafeToken(value, "referenceDataVersion", 96);
  }
}
