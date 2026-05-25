package com.wcpe.ratefeed.validation;

import com.wcpe.ratefeed.domain.RateFeedModels.RatePricePoint;
import com.wcpe.ratefeed.domain.RateFeedModels.*;

import java.util.*;

/**
 * G-005: RateSheetValidator — orchestrates the full validation suite.
 * D-002 fix: uses result objects from checkers instead of reading stale state.
 *
 * Checks:
 *   - Completeness (missings)
 *   - Duplicates (noteRate + lockPeriod pairs)
 *   - Range (out-of-bounds values)
 *   - Structural validity (column count, header presence)
 */
public class RateSheetValidator {
  private final CompletenessChecker completenessChecker = new CompletenessChecker();
  private final DuplicateChecker duplicateChecker = new DuplicateChecker();
  private final RangeChecker rangeChecker = new RangeChecker();

  /**
   * Validates raw parsed points. Returns structured result with per-error details.
   *
   * @param points parsed price points from RateSheetParser
   * @param gridHash the gridHash produced during parsing
   * @return structured validation result
   */
  public ValidationResultResponse validate(List<RatePricePoint> points, String gridHash) {
    List<ValidationErrorDetail> errors = new ArrayList<>();
    List<ValidationWarningDetail> warnings = new ArrayList<>();

    // Completeness check (nulls) — D-002 fix: use result object
    CompletenessChecker.CompletenessCheckResult completenessResult = completenessChecker.check(points);
    for (CompletenessChecker.CompletenessError e : completenessResult.errors()) {
      errors.add(new ValidationErrorDetail(e.code(), e.message(), e.row(), e.field()));
    }

    // Duplicate pair check — D-002 fix: use result object
    DuplicateChecker.DuplicateCheckResult dupResult = duplicateChecker.check(points);
    for (DuplicateChecker.DuplicateError e : dupResult.errors()) {
      errors.add(new ValidationErrorDetail(e.code(), e.message(), e.firstRow(), e.field()));
    }

    // Range check — D-002 fix: use result object
    RangeChecker.RangeCheckResult rangeResult = rangeChecker.check(points);
    for (RangeChecker.RangeError e : rangeResult.errors()) {
      errors.add(new ValidationErrorDetail(e.code(), e.message(), e.row(), e.field()));
    }

    // Structural warnings (non-fatal advisories)
    if (points.size() < 2) {
      warnings.add(new ValidationWarningDetail("SMALL_GRID",
          "Grid has fewer than 2 price points; may not cover expected rate/lock combinations"));
    }
    if (points.isEmpty()) {
      errors.add(new ValidationErrorDetail("EMPTY_GRID", "Grid has zero price points after parsing", 0, "_grid"));
    }

    boolean valid = errors.isEmpty();

    ValidationResultDetail detail = new ValidationResultDetail(
        points.size(),
        points.size(),
        gridHash,
        errors,
        warnings,
        dupResult.duplicateCount(),     // D-002: from result, not stale state
        completenessResult.missingCellCount(), // D-002: from result, not stale state
        rangeResult.outOfRangeCount(),  // D-002: from result, not stale state
        valid
    );

    return new ValidationResultResponse(null, valid ? "VALIDATED" : "PARSING", detail);
  }

  /** Standalone validation without gridHash. Useful for pre-parse header validation. */
  public ValidationResultResponse validateHeaders(String[] headerTokens) {
    List<ValidationErrorDetail> errors = new ArrayList<>();
    List<ValidationWarningDetail> warnings = new ArrayList<>();

    if (headerTokens == null || headerTokens.length == 0) {
      errors.add(new ValidationErrorDetail("NO_HEADERS", "Header row is empty", 0, "headers"));
    } else {
      // Check for structural validity: column count
      if (headerTokens.length < 3) {
        errors.add(new ValidationErrorDetail("INSUFFICIENT_COLUMNS",
            "Header has " + headerTokens.length + " columns; minimum 3 required", 0, "headers"));
      }
    }

    boolean valid = errors.isEmpty();
    ValidationResultDetail detail = new ValidationResultDetail(0, 0, null, errors, warnings, 0, 0, 0, valid);
    return new ValidationResultResponse(null, valid ? "VALIDATED" : "PARSING", detail);
  }
}
