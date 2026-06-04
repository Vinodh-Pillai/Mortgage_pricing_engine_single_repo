package com.wcpe.mladvisory;

import java.util.Optional;

public record MlAdvisoryResult<T>(boolean valid, Optional<T> value, Optional<String> errorCode) {
  public static <T> MlAdvisoryResult<T> success(T value) {
    return new MlAdvisoryResult<>(true, Optional.of(value), Optional.empty());
  }

  public static <T> MlAdvisoryResult<T> failure(String errorCode) {
    return new MlAdvisoryResult<>(false, Optional.empty(), Optional.of(errorCode));
  }
}
