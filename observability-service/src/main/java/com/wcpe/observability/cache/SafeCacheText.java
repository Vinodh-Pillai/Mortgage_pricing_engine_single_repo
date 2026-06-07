package com.wcpe.observability.cache;

import java.util.Locale;
import java.util.Objects;

final class SafeCacheText {
  private SafeCacheText() {}

  static String requireSafeToken(String raw, String fieldName, int maxLength) {
    String value = Objects.requireNonNull(raw, fieldName + " is required").strip();
    if (value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " is required");
    }
    if (value.length() > maxLength) {
      throw new IllegalArgumentException(fieldName + " exceeds safe length");
    }
    if (hasUnsafeCharacters(value) || looksSensitive(value)) {
      throw new IllegalArgumentException(fieldName + " contains unsafe cache metadata");
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
        || lower.contains("loanpayload")
        || lower.contains("loan_payload")
        || lower.contains("password")
        || lower.contains("credential")
        || lower.contains("secret")
        || lower.contains("token")
        || lower.contains("authorization")
        || lower.contains("account");
  }

  private static boolean hasUnsafeCharacters(String value) {
    return value.chars().anyMatch(ch -> Character.isWhitespace(ch) || ch == '@' || ch == '\\');
  }
}
