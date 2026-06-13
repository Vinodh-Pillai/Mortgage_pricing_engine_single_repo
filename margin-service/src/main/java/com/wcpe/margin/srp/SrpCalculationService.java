package com.wcpe.margin.srp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class SrpCalculationService {
  private static final BigDecimal BPS_PER_POINT = new BigDecimal("100");
  private static final BigDecimal BPS_DOLLAR_DIVISOR = new BigDecimal("10000");

  public SrpResult calculate(SrpRule rule, SrpInputs inputs) {
    Objects.requireNonNull(rule, "rule is required");
    Objects.requireNonNull(inputs, "inputs are required");
    Objects.requireNonNull(inputs.loanAmount(), "loanAmount is required");

    BigDecimal srpBps = spreadBps(rule, inputs);
    if (rule.minSpread().compareTo(rule.maxSpread()) > 0) {
      throw new SrpCalculationException("SRP_BOUND_INVALID");
    }
    srpBps = srpBps.max(rule.minSpread()).min(rule.maxSpread());

    BigDecimal srpPoints = srpBps.divide(BPS_PER_POINT, rule.roundingScale(), RoundingMode.HALF_UP);
    BigDecimal srpDollars = srpBps.multiply(inputs.loanAmount()).divide(BPS_DOLLAR_DIVISOR, 2, RoundingMode.HALF_UP);

    return new SrpResult(rule.reasonCode(), srpBps, srpPoints, srpDollars, inputs.noteRate(), inputs.servicingRate());
  }

  private static BigDecimal spreadBps(SrpRule rule, SrpInputs inputs) {
    if (inputs.servicingRate() == null) {
      return rule.servicingRateSpread();
    }
    Objects.requireNonNull(inputs.noteRate(), "noteRate is required when servicingRate is provided");
    return inputs.noteRate().subtract(inputs.servicingRate()).movePointRight(2);
  }

  public static final class SrpCalculationException extends RuntimeException {
    public SrpCalculationException(String message) {
      super(message);
    }
  }
}
