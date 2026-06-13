package com.wcpe.pricingbff.los;

class LosValidationException extends RuntimeException {
  private final String code;

  LosValidationException(String code, String message) {
    super(message);
    this.code = code;
  }

  String code() {
    return code;
  }
}
