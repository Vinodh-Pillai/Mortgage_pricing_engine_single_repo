package com.wcpe.observability.backpressure;

import java.util.Locale;
import java.util.Objects;

final class SafeBackpressureText {
  private SafeBackpressureText() {}

  static String requireSafeToken(String raw, String fieldName, int maxLength) {
    String value = Objects.requireNonNull(raw, fieldName + " is required").strip();
    if (value.isBlank() || value.length() > maxLength) {
      throw new IllegalArgumentException(fieldName + " is required and must fit safe length");
    }
    if (!value.matches("[A-Za-z0-9._:-]+") || looksSensitive(value)) {
      throw new IllegalArgumentException(fieldName + " contains unsafe metadata");
    }
    return value;
  }

  static boolean looksSensitive(String raw) {
    String lower = raw.toLowerCase(Locale.ROOT);
    return lower.contains("borrower")
        || lower.contains("customer")
        || lower.contains("ssn")
        || lower.contains("email")
        || lower.contains("address")
        || lower.contains("password")
        || lower.contains("credential")
        || lower.contains("secret")
        || lower.contains("token")
        || lower.contains("authorization");
  }
}
