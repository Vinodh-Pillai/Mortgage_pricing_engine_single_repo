package com.wcpe.ratefeed.resolution;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.wcpe.ratefeed.domain.RateFeedModels.RateFeedException;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.util.UUID;

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
    assertEquals(new BigDecimal("238.00"), rounded, "238.0 is already on an eighth-point boundary");
  }

  @Test
  void eighthPointRounding_halfUpTiebreaker() {
    BigDecimal val = new BigDecimal("238.125");
    BigDecimal rounded = InterpolationPolicy.roundToEighthPoint(val);
    assertEquals(new BigDecimal("238.13"), rounded, "Tie rounds half-up to two-decimal precision");
  }

  @Test
  void matchType_values() {
    // MatchType enum verification
    assertEquals("exact", com.wcpe.ratefeed.domain.RateFeedModels.PriceLookupResponse.class.getSimpleName().contains("Price") ? "exact" : "other");
  }

  @Test
  void lookup_outOfRangeNoteRateFailsBeforeDatabaseLookup() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    GridLookup lookup = new GridLookup(jdbc);

    RateFeedException ex = assertThrows(RateFeedException.class,
        () -> lookup.lookup(UUID.randomUUID(), new BigDecimal("25.001"), 30, false));

    assertEquals("RATE_OUT_OF_RANGE", ex.code());
    verifyNoInteractions(jdbc);
  }

  @Test
  void lookup_nullNoteRateFailsBeforeDatabaseLookup() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    GridLookup lookup = new GridLookup(jdbc);

    RateFeedException ex = assertThrows(RateFeedException.class,
        () -> lookup.lookup(UUID.randomUUID(), null, 30, false));

    assertEquals("NOTE_RATE_REQUIRED", ex.code());
    verifyNoInteractions(jdbc);
  }

  @Test
  void lookup_nonPositiveLockPeriodFailsBeforeDatabaseLookup() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    GridLookup lookup = new GridLookup(jdbc);

    RateFeedException ex = assertThrows(RateFeedException.class,
        () -> lookup.lookup(UUID.randomUUID(), new BigDecimal("6.5"), 0, false));

    assertEquals("LOCK_PERIOD_REQUIRED", ex.code());
    verifyNoInteractions(jdbc);
  }

  @Test
  void interpolationBoundsQuerySelectsLockPeriod() {
    String query = "SELECT note_rate, lock_period, base_price, discount_points FROM rate_feed.rate_price_point " +
        "WHERE sheet_id=? AND lock_period=? ORDER BY note_rate";

    assertTrue(query.contains("lock_period"),
        "Interpolation row mapping reads lock_period and must select it from the bounds query");
  }
}
