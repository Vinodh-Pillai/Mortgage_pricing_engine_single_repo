package com.wcpe.ratefeed.parser;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * G-001 / Type coercion utilities.
 * Converts raw CSV string cells into typed Java values.
 *
 * Required fields: note_rate, lock_period, base_price
 * Optional fields: discount_points, yield_index
 */
public final class TypeCoercer {
  private static final MathContext MC = new MathContext(18);

  private TypeCoercer() {}

  // ── Required field coercion (throws on failure) ────────────────────────────

  /** Coerce raw string to BigDecimal for noteRate. Null/blank returns null. */
  public static BigDecimal coerceRate(String raw, int gridPosition) {
    if (raw == null || raw.isBlank()) {
      return null; // downstream validator flags it
    }
    try {
      return new BigDecimal(raw, MC);
    } catch (NumberFormatException e) {
      throw new RateCoercionException("note_rate coercion failed at position " + gridPosition + ": not a valid decimal", e);
    }
  }

  /** Coerce raw string to int for lockPeriod. Null/blank throws. */
  public static int coerceLockPeriod(String raw, int gridPosition) {
    if (raw == null || raw.isBlank()) {
      throw new RateCoercionException("lock_period is required at position " + gridPosition);
    }
    try {
      return Integer.parseInt(raw.trim());
    } catch (NumberFormatException e) {
      throw new RateCoercionException("lock_period coercion failed at position " + gridPosition + ": " + raw, e);
    }
  }

  /** Coerce raw string to BigDecimal for basePrice. Null/blank returns null. */
  public static BigDecimal coerceBasePrice(String raw, int gridPosition) {
    if (raw == null || raw.isBlank()) {
      return null; // downstream validator flags it
    }
    try {
      return new BigDecimal(raw, MC);
    } catch (NumberFormatException e) {
      throw new RateCoercionException("base_price coercion failed at position " + gridPosition + ": not a valid decimal", e);
    }
  }

  // ── Optional field coercion (returns null on failure) ──────────────────────

  /** Coerce nullable optional discount points. Returns null on empty or bad value. */
  public static BigDecimal coerceNullableOptionalDiscountPoints(String raw, int gridPosition) {
    if (raw == null || raw.isBlank()) return null;
    try {
      return new BigDecimal(raw, MC);
    } catch (NumberFormatException ignored) {
      return null; // silently ignore bad optional values
    }
  }

  /** Coerce nullable optional yield index. Returns null on empty or bad value. */
  public static BigDecimal coerceNullableYieldIndex(String raw, int gridPosition) {
    if (raw == null || raw.isBlank()) return null;
    try {
      return new BigDecimal(raw, MC);
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  // ── Generic result-wrapping coercion (for other uses) ──────────────────────

  public record CoercionResult(BigDecimal value, boolean success, String error) {}

  public static CoercionResult coerceOptionalBigDecimal(String raw) {
    if (raw == null || raw.isBlank()) return new CoercionResult(null, true, null);
    try {
      return new CoercionResult(new BigDecimal(raw, MC), true, null);
    } catch (NumberFormatException e) {
      return new CoercionResult(null, false, "not a valid number: " + raw);
    }
  }

  // ── Custom exception ────────────────────────────────────────────────────────

  static class RateCoercionException extends RuntimeException {
    RateCoercionException(String message, Throwable cause) {
      super(message, cause);
    }
    RateCoercionException(String message) {
      super(message);
    }
  }
}
