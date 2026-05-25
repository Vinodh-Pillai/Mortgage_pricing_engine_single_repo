package com.wcpe.ratefeed.validation;

import com.wcpe.ratefeed.domain.RateFeedModels.RatePricePoint;

import java.math.BigDecimal;
import java.util.*;

/**
 * G-005: Checks for out-of-range values.
 * NoteRate: [0.005, 25.0], basePrice: [0, 3840] BP, lockPeriod: [7, 180] days
 * D-002 fix: returns count as part of result instead of maintaining internal state.
 */
public class RangeChecker {
  public static final BigDecimal MIN_RATE = new BigDecimal("0.005");
  public static final BigDecimal MAX_RATE = new BigDecimal("25.0");
  static final BigDecimal MIN_PRICE = BigDecimal.ZERO;
  static final BigDecimal MAX_PRICE = new BigDecimal("3840");
  static final int MIN_LOCK = 7;
  static final int MAX_LOCK = 180;

  /** Returns errors and count for out-of-range values. */
  public RangeCheckResult check(List<RatePricePoint> points) {
    List<RangeError> errors = new ArrayList<>();
    int oorCount = 0;

    for (int i = 0; i < points.size(); i++) {
      RatePricePoint p = points.get(i);
      int row = i + 1;

      // Rate range check
      if (p.noteRate() != null && (p.noteRate().compareTo(MIN_RATE) < 0 || p.noteRate().compareTo(MAX_RATE) > 0)) {
        errors.add(new RangeError("RATE_OUT_OF_RANGE",
            "noteRate " + p.noteRate() + " is outside [" + MIN_RATE + ", " + MAX_RATE + "] at row " + row,
            row, "noteRate"));
        oorCount++;
      }

      // Price range check
      if (p.basePrice() != null && (p.basePrice().compareTo(MIN_PRICE) < 0 || p.basePrice().compareTo(MAX_PRICE) > 0)) {
        errors.add(new RangeError("PRICE_OUT_OF_RANGE",
            "basePrice " + p.basePrice() + " is outside [" + MIN_PRICE + ", " + MAX_PRICE + "] at row " + row,
            row, "basePrice"));
        oorCount++;
      }

      // Lock period range check
      if (p.lockPeriod() < MIN_LOCK || p.lockPeriod() > MAX_LOCK) {
        errors.add(new RangeError("LOCK_PERIOD_OUT_OF_RANGE",
            "lockPeriod " + p.lockPeriod() + " is outside [" + MIN_LOCK + ", " + MAX_LOCK + "] at row " + row,
            row, "lockPeriod"));
        oorCount++;
      }
    }
    return new RangeCheckResult(errors, oorCount);
  }

  public record RangeCheckResult(List<RangeError> errors, int outOfRangeCount) {}
  public record RangeError(String code, String message, int row, String field) {}
}
