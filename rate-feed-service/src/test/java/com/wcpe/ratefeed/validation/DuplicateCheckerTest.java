package com.wcpe.ratefeed.validation;

import com.wcpe.ratefeed.domain.RateFeedModels.RatePricePoint;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Tests for DuplicateChecker — duplicate (noteRate, lockPeriod) pair detection. */
class DuplicateCheckerTest {

  private final DuplicateChecker checker = new DuplicateChecker();

  @Test
  void noDuplicates_emptyList() {
    var result = checker.check(List.of());
    assertTrue(result.errors().isEmpty());
    assertEquals(0, result.duplicateCount());
  }

  @Test
  void noDuplicates_uniquePoints() {
    List<RatePricePoint> points = List.of(
        pp("6.5", 30), pp("7.0", 30), pp("6.5", 45)
    );
    var result = checker.check(points);
    assertTrue(result.errors().isEmpty());
    assertEquals(0, result.duplicateCount());
  }

  @Test
  void duplicatePair_detected() {
    List<RatePricePoint> points = List.of(
        pp("6.5", 30, "100"), pp("6.5", 30, "200")
    );
    var result = checker.check(points);
    var errors = result.errors();
    assertEquals(1, errors.size());
    assertEquals("DUPLICATE_PAIR", errors.get(0).code());
    assertEquals(1, result.duplicateCount());
  }

  @Test
  void multipleDuplicatePairs_detected() {
    List<RatePricePoint> points = List.of(
        pp("6.5", 30, "100"), pp("6.5", 30, "200"),
        pp("7.0", 45, "110"), pp("7.0", 45, "120"),
        pp("6.5", 30, "300") // third duplicate of first pair - 2 extras total
    );
    var result = checker.check(points);
    var errors = result.errors();
    // 6.5/30 appears 3 times -> 1 error entry with 2 dups
    // 7.0/45 appears 2 times -> 1 error entry with 1 dup
    assertEquals(2, errors.size());
    assertEquals(3, result.duplicateCount());
  }

  private static RatePricePoint pp(String rate, int lock) {
    return new RatePricePoint(UUID.randomUUID(), new BigDecimal(rate), lock, new BigDecimal("100"), null, null, 1);
  }

  private static RatePricePoint pp(String rate, int lock, String price) {
    return new RatePricePoint(UUID.randomUUID(), new BigDecimal(rate), lock, new BigDecimal(price), null, null, 1);
  }
}
