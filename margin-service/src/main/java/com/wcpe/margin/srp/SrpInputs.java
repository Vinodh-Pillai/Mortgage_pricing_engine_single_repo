package com.wcpe.margin.srp;

import com.wcpe.margin.CompanyMarginPolicyService.MarginScope;
import java.math.BigDecimal;

public record SrpInputs(
    BigDecimal noteRate,
    BigDecimal servicingRate,
    BigDecimal loanAmount,
    int monthsRemaining,
    MarginScope scope) {
  public SrpInputs(BigDecimal noteRate, BigDecimal servicingRate, BigDecimal loanAmount, int monthsRemaining) {
    this(noteRate, servicingRate, loanAmount, monthsRemaining, new MarginScope("*", "*", "*", "*", "*", "*", "*"));
  }
}
