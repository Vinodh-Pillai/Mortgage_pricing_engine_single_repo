package com.wcpe.ratefeed.validation;

import com.wcpe.ratefeed.domain.RateFeedModels.RatePricePoint;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Tests for RangeChecker — out-of-bounds detection. */
class RangeCheckerTest {

  private final RangeChecker checker = new RangeChecker();

  @Test
  void validRange_inBounds_passes() {
    List<RatePricePoint> points = List.of(
        new RatePricePoint(UUID.randomUUID(), new BigDecimal("2.5"), 30, new BigDecimal("100"), null, null, 1)
    );
    var result = checker.check(points);
    assertTrue(result.errors().isEmpty());
    assertEquals(0, result.outOfRangeCount());
  }

  @Test
  void rateTooLow_detected() {
    List<RatePricePoint> points = List.of(
        new RatePricePoint(UUID.randomUUID(), new BigDecimal("0.001"), 30, new BigDecimal("100"), null, null, 1)
    );
    var result = checker.check(points);
    var errors = result.errors();
    assertFalse(errors.isEmpty());
    assertEquals("RATE_OUT_OF_RANGE", errors.get(0).code());
    assertEquals(1, result.outOfRangeCount());
  }

  @Test
  void rateTooHigh_detected() {
    List<RatePricePoint> points = List.of(
        new RatePricePoint(UUID.randomUUID(), new BigDecimal("26.0"), 30, new BigDecimal("100"), null, null, 1)
    );
    var errors = checker.check(points).errors();
    assertEquals("RATE_OUT_OF_RANGE", errors.get(0).code());
  }

  @Test
  void priceNegative_detected() {
    List<RatePricePoint> points = List.of(
        new RatePricePoint(UUID.randomUUID(), new BigDecimal("5.0"), 30, new BigDecimal("-10"), null, null, 1)
    );
    var errors = checker.check(points).errors();
    assertFalse(errors.isEmpty());
    assertEquals("PRICE_OUT_OF_RANGE", errors.get(0).code());
  }

  @Test
  void priceTooHigh_detected() {
    List<RatePricePoint> points = List.of(
        new RatePricePoint(UUID.randomUUID(), new BigDecimal("5.0"), 30, new BigDecimal("4000"), null, null, 1)
    );
    var errors = checker.check(points).errors();
    assertFalse(errors.isEmpty());
    assertEquals("PRICE_OUT_OF_RANGE", errors.get(0).code());
  }

  @Test
  void lockPeriodTooLow_detected() {
    List<RatePricePoint> points = List.of(
        new RatePricePoint(UUID.randomUUID(), new BigDecimal("5.0"), 5, new BigDecimal("100"), null, null, 1)
    );
    var errors = checker.check(points).errors();
    assertFalse(errors.isEmpty());
    assertEquals("LOCK_PERIOD_OUT_OF_RANGE", errors.get(0).code());
  }

  @Test
  void lockPeriodTooHigh_detected() {
    List<RatePricePoint> points = List.of(
        new RatePricePoint(UUID.randomUUID(), new BigDecimal("5.0"), 200, new BigDecimal("100"), null, null, 1)
    );
    var errors = checker.check(points).errors();
    assertFalse(errors.isEmpty());
    assertEquals("LOCK_PERIOD_OUT_OF_RANGE", errors.get(0).code());
  }

  @Test
  void boundaryValues_pass() {
    List<RatePricePoint> points = List.of(
        new RatePricePoint(UUID.randomUUID(), new BigDecimal("0.005"), 7, new BigDecimal("0"), null, null, 1),   // min boundaries
        new RatePricePoint(UUID.randomUUID(), new BigDecimal("25.0"), 180, new BigDecimal("3840"), null, null, 2)  // max boundaries
    );
    var result = checker.check(points);
    assertTrue(result.errors().isEmpty());
    assertEquals(0, result.outOfRangeCount());
  }

  @Test
  void nullOptionalFields_checkedOnlyWhenNonNull() {
    // null noteRate and basePrice are not checked by range (null guard)
    // lockPeriod is int default 0 -> detected as out of range (check below handles separately)
    List<RatePricePoint> points = List.of(
        new RatePricePoint(UUID.randomUUID(), null, 30, null, null, null, 1)
    );
    // lockPeriod 30 is in range, noteRate null is skipped, basePrice null is skipped
    assertTrue(checker.check(points).errors().isEmpty());
  }
}
