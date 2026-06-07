package com.wcpe.observability.cache;

import java.util.Objects;
import java.util.regex.Pattern;

public record PricingResultHash(String value) {
  private static final Pattern SHA_256_HEX = Pattern.compile("[a-f0-9]{64}");

  public PricingResultHash {
    value = Objects.requireNonNull(value, "pricing result hash is required").strip();
    if (!SHA_256_HEX.matcher(value).matches()) {
      throw new IllegalArgumentException("pricing result hash must be lowercase SHA-256 hex");
    }
  }
}
