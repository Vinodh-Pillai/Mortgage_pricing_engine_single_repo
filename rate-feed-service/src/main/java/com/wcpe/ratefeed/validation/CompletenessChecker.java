package com.wcpe.ratefeed.validation;

import com.wcpe.ratefeed.domain.RateFeedModels.RatePricePoint;

import java.math.BigDecimal;
import java.util.*;

/**
 * G-005: Checks for NULL required fields (noteRate, basePrice, lockPeriod).
 * D-002 fix: returns count as part of result instead of maintaining internal state.
 */
public class CompletenessChecker {

  /** Returns errors and count for null required fields. */
  public CompletenessCheckResult check(List<RatePricePoint> points) {
    List<CompletenessError> errors = new ArrayList<>();
    int missingCount = 0;

    for (int i = 0; i < points.size(); i++) {
      RatePricePoint p = points.get(i);
      int row = i + 1;

      if (p.noteRate() == null) {
        errors.add(new CompletenessError("MISSING_NOTE_RATE", "noteRate is null at row " + row, row, "noteRate"));
        missingCount++;
      }
      if (p.basePrice() == null) {
        errors.add(new CompletenessError("MISSING_BASE_PRICE", "basePrice is null at row " + row, row, "basePrice"));
        missingCount++;
      }
      // lockPeriod is primitive int; 0 indicates missing/unparsed
      if (p.lockPeriod() == 0) {
        errors.add(new CompletenessError("MISSING_LOCK_PERIOD", "lockPeriod is missing at row " + row, row, "lock_period"));
        missingCount++;
      }
      // gridPosition is primitive; 0 may indicate issue
      if (p.gridPosition() <= 0) {
        errors.add(new CompletenessError("INVALID_GRID_POSITION", "Invalid gridPosition at row " + row, row, "gridPosition"));
        missingCount++;
      }
    }
    return new CompletenessCheckResult(errors, missingCount);
  }

  public record CompletenessCheckResult(List<CompletenessError> errors, int missingCellCount) {}
  public record CompletenessError(String code, String message, int row, String field) {}
}
