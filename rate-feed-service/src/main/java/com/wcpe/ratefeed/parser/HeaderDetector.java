package com.wcpe.ratefeed.parser;

import java.util.*;

/**
 * G-001: Header detection — maps CSV header tokens to canonical field names.
 *
 * Required fields: note_rate, lock_period, base_price
 * Optional fields: discount_points, yield_index
 */
public final class HeaderDetector {
  private HeaderDetector() {}

  private static final Set<String> REQUIRED = Set.of("note_rate", "lock_period", "base_price");
  private static final Set<String> CANONICAL = Set.of("note_rate", "lock_period", "base_price", "discount_points", "yield_index");

  private static final Map<String, String> ALIASES = Map.of(
      "rate", "note_rate",
      "interest_rate", "note_rate",
      "lock_period_days", "lock_period",
      "price", "base_price",
      "base_price_bp", "base_price",
      "discount_pts", "discount_points",
      "discount_points", "discount_points",
      "yield", "yield_index",
      "yield_index", "yield_index"
  );

  /**
   * Returns an immutable map from column index → canonical field name.
   *
   * Throws if any of the required fields (note_rate, lock_period, base_price) are missing.
   */
  public static Map<Integer, String> mapHeaders(String[] headerTokens) {
    Map<Integer, String> mapping = new LinkedHashMap<>();
    Set<String> found = new HashSet<>();

    for (int i = 0; i < headerTokens.length; i++) {
      String raw = headerTokens[i].trim();
      if (raw.isEmpty()) continue;
      String canonical = resolveCanonical(raw);
      if (canonical != null) {
        if (!found.add(canonical)) {
          throw new IllegalArgumentException("HeaderDuplicateMappedField: '" + canonical + "' is mapped by more than one header.");
        }
        mapping.put(i, canonical);
      }
    }

    for (String required : REQUIRED) {
      if (!mapping.values().contains(required)) {
        throw new IllegalArgumentException(
            String.format("HeaderMissingRequiredField: '%s' is required but not found. Found: %s",
                required, mapping.values()));
      }
    }

    return Map.copyOf(mapping);
  }

  private static String resolveCanonical(String header) {
    String lower = header.toLowerCase(Locale.ROOT).replace(' ', '_');
    if (CANONICAL.contains(lower)) return lower;
    return ALIASES.get(lower);
  }
}
