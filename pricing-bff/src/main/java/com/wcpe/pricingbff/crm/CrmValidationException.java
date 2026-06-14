package com.wcpe.pricingbff.crm;

class CrmValidationException extends RuntimeException {
  private final String code;

  CrmValidationException(String code, String message) {
    super(message);
    this.code = code;
  }

  String code() {
    return code;
  }
}
