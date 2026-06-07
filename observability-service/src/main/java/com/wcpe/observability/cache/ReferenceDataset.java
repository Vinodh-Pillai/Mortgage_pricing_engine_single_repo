package com.wcpe.observability.cache;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record ReferenceDataset(String value) {
  private static final int MAX_LENGTH = 80;
  private static final Pattern SAFE_DATASET = Pattern.compile("[a-z0-9][a-z0-9._-]{0,79}");

  public ReferenceDataset {
    value = Objects.requireNonNull(value, "dataset is required").strip().toLowerCase(Locale.ROOT);
    if (value.isBlank()) {
      throw new IllegalArgumentException("dataset is required");
    }
    if (value.length() > MAX_LENGTH || !SAFE_DATASET.matcher(value).matches()) {
      throw new IllegalArgumentException(
          "dataset must be lowercase alpha-numeric with optional '.', '_' or '-' separators");
    }
    if (SafeCacheText.looksSensitive(value)) {
      throw new IllegalArgumentException("dataset contains unsafe cache metadata");
    }
  }
}
