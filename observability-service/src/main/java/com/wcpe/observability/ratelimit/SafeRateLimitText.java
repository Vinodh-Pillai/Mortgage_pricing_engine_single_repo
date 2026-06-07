package com.wcpe.observability.ratelimit;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

final class SafeRateLimitText {
  private static final Pattern SAFE_TOKEN = Pattern.compile("[A-Za-z0-9._:-]+");
  private static final Pattern PRINCIPAL_HASH = Pattern.compile("sha256:[a-f0-9]{16,128}");

  private SafeRateLimitText() {}

  static String requireSafeToken(String raw, String fieldName, int maxLength) {
    String value = Objects.requireNonNull(raw, fieldName + " is required").strip();
    if (value.isBlank() || value.length() > maxLength || !SAFE_TOKEN.matcher(value).matches()) {
      throw new IllegalArgumentException(fieldName + " is required and must use safe metadata characters");
    }
    if (looksSensitive(value)) {
      throw new IllegalArgumentException(fieldName + " must not contain borrower PII or secret-like values");
    }
    return value;
  }

  static String requirePrincipalHash(String raw) {
    String value = requireSafeToken(raw, "principalHash", 140).toLowerCase(Locale.ROOT);
    if (!PRINCIPAL_HASH.matcher(value).matches()) {
      throw new IllegalArgumentException("principalHash must be a sha256 hash, not a raw user, token, or API key");
    }
    return value;
  }

  private static boolean looksSensitive(String raw) {
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
        || lower.contains("authorization")
        || lower.contains("api_key")
        || lower.contains("apikey");
  }
}
