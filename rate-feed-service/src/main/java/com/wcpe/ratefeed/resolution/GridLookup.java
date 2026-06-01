package com.wcpe.ratefeed.resolution;

import com.wcpe.ratefeed.domain.RateFeedModels.RatePricePoint;
import com.wcpe.ratefeed.domain.RateFeedModels.RateFeedException;
import com.wcpe.ratefeed.domain.RateFeedModels.MatchType;
import com.wcpe.ratefeed.domain.Hashing;
import com.wcpe.ratefeed.validation.RangeChecker;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.*;

/**
 * G-004 — (noteRate, lockPeriod) → basePrice lookup with interpolation.
 * D-004 fix: validates noteRate range before interpolation.
 */
@Service
public class GridLookup {
  private final JdbcTemplate jdbc;
  private static final BigDecimal MIN_RATE = RangeChecker.MIN_RATE;
  private static final BigDecimal MAX_RATE = RangeChecker.MAX_RATE;

  public GridLookup(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  /**
   * Lookup base price by (noteRate, lockPeriod).
   * If interpolate=false (default), returns exact match or 404.
   * If interpolate=true, linear interpolation between bounding points.
   * D-004 fix: rejects out-of-range noteRate for interpolation.
   */
  public PriceResult lookup(UUID sheetId, BigDecimal noteRate, int lockPeriod, boolean interpolate) {
    if (noteRate == null) {
      throw new RateFeedException(HttpStatus.BAD_REQUEST, "NOTE_RATE_REQUIRED", "noteRate is required for price lookup");
    }
    if (lockPeriod <= 0) {
      throw new RateFeedException(HttpStatus.BAD_REQUEST, "LOCK_PERIOD_REQUIRED", "lockPeriod must be greater than zero for price lookup");
    }
    // D-004: fail closed before exact lookup or interpolation can return invalid grid data.
    if (noteRate.compareTo(MIN_RATE) < 0 || noteRate.compareTo(MAX_RATE) > 0) {
      throw new RateFeedException(HttpStatus.BAD_REQUEST, "RATE_OUT_OF_RANGE",
          "noteRate " + noteRate + " is outside acceptable range [" + MIN_RATE + ", " + MAX_RATE + "]");
    }

    // Exact match
    List<RatePricePoint> exact = jdbc.query(
        "SELECT * FROM rate_feed.rate_price_point WHERE sheet_id=? AND note_rate=? AND lock_period=?",
        (rs, row) -> new RatePricePoint(rs.getObject("sheet_id", UUID.class), rs.getBigDecimal("note_rate"),
            rs.getInt("lock_period"), rs.getBigDecimal("base_price"), rs.getBigDecimal("discount_points"),
            rs.getBigDecimal("yield_index"), rs.getInt("grid_position")),
        sheetId, noteRate, lockPeriod);

    if (!exact.isEmpty()) {
      RatePricePoint p = exact.get(0);
      String rh = Hashing.sha256("price:" + p.basePrice() + ":" + noteRate + ":" + lockPeriod);
       return new PriceResult(p.basePrice(), p.discountPoints(), MatchType.EXACT.value(), rh);
    }

    if (!interpolate) {
      throw new RateFeedException(HttpStatus.NOT_FOUND, "NO_EXACT_MATCH",
          "No exact match for noteRate=" + noteRate + " lockPeriod=" + lockPeriod);
    }

    // Interpolation: find bounding rates for same lockPeriod
    List<RatePricePoint> bounds = jdbc.query(
        "SELECT note_rate, lock_period, base_price, discount_points FROM rate_feed.rate_price_point WHERE sheet_id=? AND lock_period=? ORDER BY note_rate",
        (rs, row) -> new RatePricePoint(null, rs.getBigDecimal("note_rate"), rs.getInt("lock_period"),
            rs.getBigDecimal("base_price"), rs.getBigDecimal("discount_points"), null, 0),
        sheetId, lockPeriod);

    if (bounds.size() < 2) {
      throw new RateFeedException(HttpStatus.NOT_FOUND, "NO_BOUNDS_FOR_INTERPOLATION",
          "Insufficient grid points for interpolation");
    }

    BigDecimal lowerRate = null, upperRate = null;
    BigDecimal lowerPrice = null, upperPrice = null;
    BigDecimal lowerDisc = null, upperDisc = null;

    for (RatePricePoint p : bounds) {
      if (p.noteRate().compareTo(noteRate) <= 0) {
        lowerRate = p.noteRate(); lowerPrice = p.basePrice(); lowerDisc = p.discountPoints();
      }
      if (p.noteRate().compareTo(noteRate) >= 0 && upperRate == null) {
        upperRate = p.noteRate(); upperPrice = p.basePrice(); upperDisc = p.discountPoints();
      }
    }

    if (lowerRate == null || upperRate == null || lowerRate.equals(upperRate)) {
      throw new RateFeedException(HttpStatus.NOT_FOUND, "NO_BOUNDS_FOR_INTERPOLATION", "Cannot interpolate");
    }

    // Linear interpolation
    BigDecimal range = upperRate.subtract(lowerRate);
    BigDecimal fraction = noteRate.subtract(lowerRate).divide(range, MathContext.DECIMAL128);
    BigDecimal price = lowerPrice.add(upperPrice.subtract(lowerPrice).multiply(fraction));

    // Round to nearest 1/8th point (12.5 BP)
    price = InterpolationPolicy.roundToEighthPoint(price);

    BigDecimal disc = null;
    if (lowerDisc != null && upperDisc != null) {
      disc = lowerDisc.add(upperDisc.subtract(lowerDisc).multiply(fraction));
    }

    String rh = Hashing.sha256("interpolated:" + price + ":" + noteRate + ":" + lockPeriod);
    return new PriceResult(price, disc, MatchType.INTERPOLATED.value(), rh);
  }

  public record PriceResult(
      BigDecimal basePrice, BigDecimal discountPoints, String match, String resultHash
  ) {}
}
