package com.wcpe.ratefeed.validation;

import com.wcpe.ratefeed.domain.RatePricePoint;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Tests for CompletenessChecker — null required field detection. */
class CompletenessCheckerTest {

  private final CompletenessChecker checker = new CompletenessChecker();

  @Test
  void completePoint_passes() {
    List<RatePricePoint> points = List.of(
        new RatePricePoint(UUID.randomUUID(), new BigDecimal("6.5"), 30, new BigDecimal("100"), null, null, 1)
    );
    assertTrue(checker.check(points).isEmpty());
    assertEquals(0, checker.missingCellCount());
  }

  @Test
  void nullNoteRate_detected() {
    List<RatePricePoint> points = List.of(
        new RatePricePoint(UUID.randomUUID(), null, 30, new BigDecimal("100"), null, null, 1)
    );
    var errors = checker.check(points);
    assertEquals(1, errors.size());
    assertEquals("MISSING_NOTE_RATE", errors.get(0).code());
  }

  @Test
  void nullBasePrice_detected() {
    List<RatePricePoint> points = List.of(
        new RatePricePoint(UUID.randomUUID(), new BigDecimal("6.5"), 30, null, null, null, 1)
    );
    var errors = checker.check(points);
    assertEquals(1, errors.size());
    assertEquals("MISSING_BASE_PRICE", errors.get(0).code());
  }

  @Test
  void zeroLockPeriod_detected() {
    List<RatePricePoint> points = List.of(
        new RatePricePoint(UUID.randomUUID(), new BigDecimal("6.5"), 0, new BigDecimal("100"), null, null, 1)
    );
    var errors = checker.check(points);
    assertEquals(1, errors.size());
    assertEquals("MISSING_LOCK_PERIOD", errors.get(0).code());
  }

  @Test
  void zeroGridPosition_detected() {
    List<RatePricePoint> points = List.of(
        new RatePricePoint(UUID.randomUUID(), new BigDecimal("6.5"), 30, new BigDecimal("100"), null, null, 0)
    );
    var errors = checker.check(points);
    assertEquals(1, errors.size());
    assertEquals("INVALID_GRID_POSITION", errors.get(0).code());
  }

  @Test
  void multipleMissingFields_allDetected() {
    List<RatePricePoint> points = List.of(
        new RatePricePoint(UUID.randomUUID(), null, 0, null, null, null, 0)
    );
    var errors = checker.check(points);
    // null noteRate + lockPeriod 0 + null basePrice + gridPosition 0 = 4 errors
    assertEquals(4, errors.size());
    assertEquals(4, checker.missingCellCount());
  }

  @Test
  void emptyList_noErrors() {
    assertTrue(checker.check(List.of()).isEmpty());
    assertEquals(0, checker.missingCellCount());
  }

  @Test
  void multiplePoints_mixedCompleteness() {
    List<RatePricePoint> points = List.of(
        new RatePricePoint(UUID.randomUUID(), new BigDecimal("6.5"), 30, new BigDecimal("100"), null, null, 1), // valid
        new RatePricePoint(UUID.randomUUID(), null, 0, null, null, null, 0) // all missing
    );
    var errors = checker.check(points);
    assertEquals(4, errors.size()); // only from second point
  }
}
