package com.wcpe.quote.los;

class LosQuoteValidationException extends RuntimeException {
  private final String code;

  LosQuoteValidationException(String code, String message) {
    super(message);
    this.code = code;
  }

  String code() {
    return code;
  }
}
