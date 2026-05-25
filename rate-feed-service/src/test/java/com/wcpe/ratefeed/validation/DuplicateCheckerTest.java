package com.wcpe.ratefeed.validation;

import com.wcpe.ratefeed.domain.RatePricePoint;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Tests for DuplicateChecker — duplicate (noteRate, lockPeriod) pair detection. */
class DuplicateCheckerTest {

  private final DuplicateChecker checker = new DuplicateChecker();

  @Test
  void noDuplicates_emptyList() {
    assertTrue(checker.check(List.of()).isEmpty());
    assertEquals(0, checker.duplicateCount());
  }

  @Test
  void noDuplicates_uniquePoints() {
    List<RatePricePoint> points = List.of(
        pp(6.5, 30), pp(7.0, 30), pp(6.5, 45)
    );
    assertTrue(checker.check(points).isEmpty());
    assertEquals(0, checker.duplicateCount());
  }

  @Test
  void duplicatePair_detected() {
    List<RatePricePoint> points = List.of(
        pp(6.5, 30, 100), pp(6.5, 30, 200)
    );
    var errors = checker.check(points);
    assertEquals(1, errors.size());
    assertEquals("DUPLICATE_PAIR", errors.get(0).code());
    assertEquals(1, checker.duplicateCount());
  }

  @Test
  void multipleDuplicatePairs_detected() {
    List<RatePricePoint> points = List.of(
        pp(6.5, 30, 100), pp(6.5, 30, 200),
        pp(7.0, 45, 110), pp(7.0, 45, 120),
        pp(6.5, 30, 300) // third duplicate of first pair - 2 extras total
    );
    var errors = checker.check(points);
    // 6.5/30 appears 3 times -> 1 error entry with 2 dups
    // 7.0/45 appears 2 times -> 1 error entry with 1 dup
    assertEquals(2, errors.size());
    assertEquals(3, checker.duplicateCount());
  }

  private static RatePricePoint pp(BigDecimal rate, int lock) {
    return new RatePricePoint(UUID.randomUUID(), rate, lock, new BigDecimal("100"), null, null, 1);
  }

  private static RatePricePoint pp(BigDecimal rate, int lock, BigDecimal price) {
    return new RatePricePoint(UUID.randomUUID(), rate, lock, price, null, null, 1);
  }
}
