package com.wcpe.observability.cache;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record TenantCacheNamespace(String value) {
  private static final int MAX_LENGTH = 80;
  private static final Pattern SAFE_NAMESPACE = Pattern.compile("[a-z0-9][a-z0-9._-]{0,79}");

  public TenantCacheNamespace {
    value = normalize(value);
    if (!SAFE_NAMESPACE.matcher(value).matches()) {
      throw new IllegalArgumentException(
          "namespace must be lowercase alpha-numeric with optional '.', '_' or '-' separators");
    }
    if (value.length() > MAX_LENGTH) {
      throw new IllegalArgumentException("namespace must not exceed " + MAX_LENGTH + " characters");
    }
  }

  private static String normalize(String raw) {
    String normalized = Objects.requireNonNull(raw, "namespace is required")
        .strip()
        .toLowerCase(Locale.ROOT);
    if (normalized.isBlank()) {
      throw new IllegalArgumentException("namespace is required");
    }
    return normalized;
  }
}
