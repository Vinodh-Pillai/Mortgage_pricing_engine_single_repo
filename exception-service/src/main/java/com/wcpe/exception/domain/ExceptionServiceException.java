package com.wcpe.exception.domain;

/**
 * Deterministic exception for validation and transition errors.
 */
public class ExceptionServiceException extends RuntimeException {
  private final String code;

  public ExceptionServiceException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String code() { return code; }
}
