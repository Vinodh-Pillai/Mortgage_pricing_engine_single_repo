package com.wcpe.governance;

import java.util.Optional;

public final class GovernanceValidationResult<T> {
  private final T value;
  private final String error;

  private GovernanceValidationResult(T value, String error) {
    this.value = value;
    this.error = error;
  }

  public static <T> GovernanceValidationResult<T> success(T value) {
    return new GovernanceValidationResult<>(value, null);
  }

  public static <T> GovernanceValidationResult<T> failure(String error) {
    return new GovernanceValidationResult<>(null, error);
  }

  public boolean valid() {
    return error == null;
  }

  public Optional<T> value() {
    return Optional.ofNullable(value);
  }

  public Optional<String> error() {
    return Optional.ofNullable(error);
  }
}
