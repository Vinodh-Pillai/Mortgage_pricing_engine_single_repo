package com.wcpe.observability.scenariohash;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public record VersionGraph(Map<String, String> versions) {
  public VersionGraph {
    Objects.requireNonNull(versions, "version graph is required");
    TreeMap<String, String> sorted = new TreeMap<>();
    for (Map.Entry<String, String> entry : versions.entrySet()) {
      String key = requireToken(entry.getKey(), "version graph key");
      String value = requireToken(entry.getValue(), "version graph value");
      sorted.put(key, value);
    }
    versions = Map.copyOf(sorted);
  }

  private static String requireToken(String raw, String fieldName) {
    String value = Objects.requireNonNull(raw, fieldName + " is required").strip();
    if (value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " is required");
    }
    if (value.length() > 128) {
      throw new IllegalArgumentException(fieldName + " exceeds safe length");
    }
    return value;
  }
}
