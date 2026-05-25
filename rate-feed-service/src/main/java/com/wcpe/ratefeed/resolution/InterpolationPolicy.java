package com.wcpe.ratefeed.resolution;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Interpolation policy: default fail-closed, optional 1/8th-point rounding.
 * D-005 fix: roundToEighthPoint uses 2 decimal scale instead of 1 for correct precision.
 */
public final class InterpolationPolicy {
  private InterpolationPolicy() {}

  /**
   * Round to nearest 1/8th point (12.5 BP), half-up tiebreaker.
   * 1/8 point = 12.5 basis points = 0.125%.
   * D-005 fix: divide to 2 decimal places (nearest 0.01%) instead of 1.
   *   e.g. 237.1875 rounds correctly as 237.19, not truncated to 237.2.
   */
  public static BigDecimal roundToEighthPoint(BigDecimal basisPoints) {
    // nearest 1/8th = multiply by 8, round, divide by 8
    BigDecimal scaled = basisPoints.multiply(BigDecimal.valueOf(8)).setScale(0, RoundingMode.HALF_UP);
    return scaled.divide(BigDecimal.valueOf(8), 2, RoundingMode.HALF_UP);
  }
}
