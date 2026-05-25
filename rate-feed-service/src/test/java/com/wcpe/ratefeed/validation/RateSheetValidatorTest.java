package com.wcpe.ratefeed.validation;

import com.wcpe.ratefeed.domain.RatePricePoint;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.*;

/** Unit tests for RateSheetValidator — covers AC-04-02 validation acceptance criteria. */
class RateSheetValidatorTest {

  private RateSheetValidator validator = new RateSheetValidator();

  @Test
  void validGrid_passes() {
    List<RatePricePoint> points = List.of(
        pp("6.5", 30, "237.50"),
        pp("7.0", 30, "250.00")
    );
    var result = validator.validate(points, "sha256:abc");
    assertTrue(result.validationResult().valid());
  }

  @Test
  void duplicatePairs_detected() {
    List<RatePricePoint> points = List.of(
        pp("6.5", 30, "237.50"),
        pp("6.5", 30, "240.00")  // duplicate (noteRate, lockPeriod)
    );
    var result = validator.validate(points, "sha256:abc");
    assertFalse(result.validationResult().valid());
    assertEquals(1, result.validationResult().duplicatePairs());
  }

  @Test
  void emptyGrid_valid() {
    var result = validator.validate(List.of(), "sha256:abc");
    assertTrue(result.validationResult().valid());
  }

  private static RatePricePoint pp(String rate, int lock, String price) {
    return new RatePricePoint(UUID.randomUUID(), new BigDecimal(rate), lock, new BigDecimal(price), null, null, 1);
  }
}
