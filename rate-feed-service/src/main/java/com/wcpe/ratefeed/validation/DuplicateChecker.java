package com.wcpe.ratefeed.validation;

import com.wcpe.ratefeed.domain.RateFeedModels.RatePricePoint;

import java.util.*;

/**
 * G-005: Detects duplicate (noteRate, lockPeriod) pairs within the grid.
 * D-002 fix: returns count as part of result instead of maintaining internal state.
 */
public class DuplicateChecker {

  /** Returns errors and count for duplicate (noteRate, lockPeriod) pairs. */
  public DuplicateCheckResult check(List<RatePricePoint> points) {
    List<DuplicateError> errors = new ArrayList<>();
    int dupCount = 0;

    // Key: "noteRate|lockPeriod" -> list of 1-based row indices
    Map<String, List<Integer>> pairRows = new LinkedHashMap<>();

    for (int i = 0; i < points.size(); i++) {
      RatePricePoint p = points.get(i);
      String key = p.noteRate().toPlainString() + "|" + p.lockPeriod();
      pairRows.computeIfAbsent(key, k -> new ArrayList<>()).add(i + 1);
    }

    for (Map.Entry<String, List<Integer>> entry : pairRows.entrySet()) {
      List<Integer> rows = entry.getValue();
      if (rows.size() > 1) {
        String[] parts = entry.getKey().split("\\|");
        dupCount += rows.size() - 1;
        errors.add(new DuplicateError(
            "DUPLICATE_PAIR",
            "Duplicate (noteRate=" + parts[0] + ", lockPeriod=" + parts[1] +
            ") at rows " + rows,
            rows.get(0), "noteRate,lockPeriod"
        ));
      }
    }
    return new DuplicateCheckResult(errors, dupCount);
  }

  public record DuplicateCheckResult(List<DuplicateError> errors, int duplicateCount) {}
  public record DuplicateError(String code, String message, int firstRow, String field) {}
}
