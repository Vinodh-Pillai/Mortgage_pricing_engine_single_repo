package com.wcpe.lock;

public final class LockServiceException extends RuntimeException {
  private final String code;

  public LockServiceException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
