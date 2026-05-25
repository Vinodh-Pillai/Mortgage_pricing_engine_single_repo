package com.wcpe.ratefeed.parser;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Type coercion tests — maps to actual TypeCoercer API:
 * coerceRate(String, int), coerceLockPeriod(String, int), coerceBasePrice(String, int)
 * coerceNullableOptionalDiscountPoints, coerceNullableYieldIndex, coerceOptionalBigDecimal -> CoercionResult
 */
class TypeCoercerNewTest {

  // ── coerceRate ──

  @Test
  void coerceRate_validDecimal() {
    assertEquals(new BigDecimal("6.125"), TypeCoercer.coerceRate("6.125", 1));
  }

  @Test
  void coerceRate_null_returnsNull() {
    assertNull(TypeCoercer.coerceRate(null, 1));
  }

  @Test
  void coerceRate_blank_returnsNull() {
    assertNull(TypeCoercer.coerceRate("", 1));
    assertNull(TypeCoercer.coerceRate("  ", 1));
  }

  @Test
  void coerceRate_nonNumeric_throws() {
    assertThrows(TypeCoercer.RateCoercionException.class, () -> TypeCoercer.coerceRate("abc", 1));
  }

  @Test
  void coerceRate_includesGridPosition() {
    try {
      TypeCoercer.coerceRate("not-a-number", 42);
      fail("should throw");
    } catch (TypeCoercer.RateCoercionException e) {
      assertTrue(e.getMessage().contains("position 42"));
    }
  }

  // ── coerceLockPeriod ──

  @Test
  void coerceLockPeriod_validInt() {
    assertEquals(30, TypeCoercer.coerceLockPeriod("30", 1));
    assertEquals(180, TypeCoercer.coerceLockPeriod("180", 1));
  }

  @Test
  void coerceLockPeriod_null_throws() {
    assertThrows(TypeCoercer.RateCoercionException.class, () -> TypeCoercer.coerceLockPeriod(null, 1));
  }

  @Test
  void coerceLockPeriod_blank_throws() {
    assertThrows(TypeCoercer.RateCoercionException.class, () -> TypeCoercer.coerceLockPeriod("", 1));
  }

  @Test
  void coerceLockPeriod_decimal_throws() {
    assertThrows(TypeCoercer.RateCoercionException.class, () -> TypeCoercer.coerceLockPeriod("30.5", 1));
  }

  @Test
  void coerceLockPeriod_nonNumeric_throws() {
    assertThrows(TypeCoercer.RateCoercionException.class, () -> TypeCoercer.coerceLockPeriod("abc", 1));
  }

  // ── coerceBasePrice ──

  @Test
  void coerceBasePrice_validDecimal() {
    assertEquals(new BigDecimal("108.5"), TypeCoercer.coerceBasePrice("108.5", 1));
  }

  @Test
  void coerceBasePrice_null_returnsNull() {
    assertNull(TypeCoercer.coerceBasePrice(null, 1));
  }

  @Test
  void coerceBasePrice_blank_returnsNull() {
    assertNull(TypeCoercer.coerceBasePrice("", 1));
  }

  @Test
  void coerceBasePrice_nonNumeric_throws() {
    assertThrows(TypeCoercer.RateCoercionException.class, () -> TypeCoercer.coerceBasePrice("bad", 1));
  }

  // ── Optional fields (null-safe) ──

  @Test
  void coerceNullableOptionalDiscountPoints_valid() {
    assertEquals(new BigDecimal("0.5"), TypeCoercer.coerceNullableOptionalDiscountPoints("0.5", 1));
  }

  @Test
  void coerceNullableOptionalDiscountPoints_null() {
    assertNull(TypeCoercer.coerceNullableOptionalDiscountPoints(null, 1));
  }

  @Test
  void coerceNullableOptionalDiscountPoints_blank() {
    assertNull(TypeCoercer.coerceNullableOptionalDiscountPoints("", 1));
  }

  @Test
  void coerceNullableOptionalDiscountPoints_invalid() {
    assertNull(TypeCoercer.coerceNullableOptionalDiscountPoints("bad", 1));
  }

  @Test
  void coerceNullableYieldIndex_valid() {
    assertEquals(new BigDecimal("1.0"), TypeCoercer.coerceNullableYieldIndex("1.0", 1));
  }

  @Test
  void coerceNullableYieldIndex_null() {
    assertNull(TypeCoercer.coerceNullableYieldIndex(null, 1));
  }

  @Test
  void coerceNullableYieldIndex_invalid() {
    assertNull(TypeCoercer.coerceNullableYieldIndex("not-a-number", 1));
  }

  // ── CoercionResult wrapper ──

  @Test
  void coerceOptionalBigDecimal_valid() {
    TypeCoercer.CoercionResult r = TypeCoercer.coerceOptionalBigDecimal("3.14");
    assertTrue(r.success());
    assertEquals(new BigDecimal("3.14"), r.value());
    assertNull(r.error());
  }

  @Test
  void coerceOptionalBigDecimal_null_blankSuccess() {
    TypeCoercer.CoercionResult r = TypeCoercer.coerceOptionalBigDecimal(null);
    assertTrue(r.success());
    assertNull(r.value());
  }

  @Test
  void coerceOptionalBigDecimal_invalid() {
    TypeCoercer.CoercionResult r = TypeCoercer.coerceOptionalBigDecimal("not-a-number");
    assertFalse(r.success());
    assertNull(r.value());
    assertNotNull(r.error());
  }
}
