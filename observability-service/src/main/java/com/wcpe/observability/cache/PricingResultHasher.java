package com.wcpe.observability.cache;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public final class PricingResultHasher {
  public PricingResultHash hash(Map<String, ?> pricingResultPayload) {
    Objects.requireNonNull(pricingResultPayload, "pricingResultPayload is required");
    return new PricingResultHash(sha256Hex(canonicalJson(pricingResultPayload, "$")));
  }

  private static String canonicalJson(Object value, String path) {
    if (value == null) {
      return "null";
    }
    if (value instanceof String string) {
      return quote(requireSafePayloadValue(string, path));
    }
    if (value instanceof Boolean bool) {
      return bool.toString().toLowerCase(Locale.ROOT);
    }
    if (value instanceof BigDecimal decimal) {
      return quote(decimal.stripTrailingZeros().toPlainString());
    }
    if (value instanceof BigInteger integer) {
      return integer.toString();
    }
    if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
      return value.toString();
    }
    if (value instanceof Float || value instanceof Double) {
      throw new IllegalArgumentException(path + " uses binary floating point; use BigDecimal for pricing result values");
    }
    if (value instanceof Instant instant) {
      return quote(instant.toString());
    }
    if (value instanceof Map<?, ?> map) {
      TreeMap<String, Object> sorted = new TreeMap<>();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        String key = SafeCacheText.requireSafeToken(
            Objects.requireNonNull(entry.getKey(), "pricing result keys are required").toString(),
            "pricing result key",
            96);
        sorted.put(key, entry.getValue());
      }
      StringBuilder builder = new StringBuilder("{");
      boolean first = true;
      for (Map.Entry<String, Object> entry : sorted.entrySet()) {
        if (!first) {
          builder.append(',');
        }
        first = false;
        builder.append(quote(entry.getKey())).append(':').append(canonicalJson(entry.getValue(), path + "." + entry.getKey()));
      }
      return builder.append('}').toString();
    }
    if (value instanceof Collection<?> collection) {
      StringBuilder builder = new StringBuilder("[");
      int index = 0;
      for (Object item : collection) {
        if (index > 0) {
          builder.append(',');
        }
        builder.append(canonicalJson(item, path + "[" + index + "]"));
        index++;
      }
      return builder.append(']').toString();
    }
    if (value instanceof Enum<?> enumValue) {
      return quote(enumValue.name().toLowerCase(Locale.ROOT));
    }
    throw new IllegalArgumentException(path + " uses unsupported pricing result value type: " + value.getClass().getName());
  }

  private static String requireSafePayloadValue(String value, String path) {
    if (SafeCacheText.looksSensitive(value) || value.length() > 160) {
      throw new IllegalArgumentException(path + " contains unsafe pricing result metadata");
    }
    return value.strip();
  }

  private static String quote(String value) {
    StringBuilder builder = new StringBuilder("\"");
    for (int index = 0; index < value.length(); index++) {
      char ch = value.charAt(index);
      switch (ch) {
        case '\\' -> builder.append("\\\\");
        case '"' -> builder.append("\\\"");
        case '\b' -> builder.append("\\b");
        case '\f' -> builder.append("\\f");
        case '\n' -> builder.append("\\n");
        case '\r' -> builder.append("\\r");
        case '\t' -> builder.append("\\t");
        default -> {
          if (ch < 0x20) {
            builder.append(String.format("\\u%04x", (int) ch));
          } else {
            builder.append(ch);
          }
        }
      }
    }
    return builder.append('"').toString();
  }

  private static String sha256Hex(String value) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder builder = new StringBuilder(digest.length * 2);
      for (byte item : digest) {
        builder.append(String.format("%02x", item));
      }
      return builder.toString();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }
}
