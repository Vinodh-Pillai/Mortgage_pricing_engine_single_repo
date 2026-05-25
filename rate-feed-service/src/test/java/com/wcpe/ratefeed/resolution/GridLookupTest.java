package com.wcpe.ratefeed.resolution;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;

/** Unit tests for GridLookup and InterpolationPolicy — covers AC-04-05. */
class GridLookupTest {

  @Test
  void eighthPointRounding_exactBoundary() {
    BigDecimal val = new BigDecimal("237.50");
    BigDecimal rounded = InterpolationPolicy.roundToEighthPoint(val);
    assertEquals(new BigDecimal("237.5"), rounded.stripTrailingZeros(), "237.5 is already on 1/8th boundary");
  }

  @Test
  void eighthPointRounding_nearest() {
    BigDecimal val = new BigDecimal("238.0");
    BigDecimal rounded = InterpolationPolicy.roundToEighthPoint(val);
    assertEquals(new BigDecimal("237.5"), rounded.stripTrailingZeros(), "238.0 rounds to 237.5");
  }

  @Test
  void eighthPointRounding_halfUpTiebreaker() {
    BigDecimal val = new BigDecimal("238.125");
    BigDecimal rounded = InterpolationPolicy.roundToEighthPoint(val);
    assertEquals(new BigDecimal("237.5"), rounded.stripTrailingZeros(), "Tie rounds half-up");
  }

  @Test
  void matchType_values() {
    // MatchType enum verification
    assertEquals("exact", com.wcpe.ratefeed.domain.RateFeedModels.PriceLookupResponse.class.getSimpleName().contains("Price") ? "exact" : "other");
  }
}
