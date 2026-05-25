package com.wcpe.ratefeed.parser;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Header detection tests — maps to actual HeaderDetector API:
 * mapHeaders(String[]) -> Map<Integer, String>
 */
class HeaderDetectorNewTest {

  @Test
  void mapHeaders_standardHeaders_allRecognized() {
    String[] headers = {"note_rate", "lock_period", "base_price"};
    Map<Integer, String> map = HeaderDetector.mapHeaders(headers);
    assertEquals(3, map.size());
    assertEquals("note_rate", map.get(0));
    assertEquals("lock_period", map.get(1));
    assertEquals("base_price", map.get(2));
  }

  @Test
  void mapHeaders_withOptionalColumns() {
    String[] headers = {"note_rate", "lock_period", "base_price", "discount_points", "yield_index"};
    Map<Integer, String> map = HeaderDetector.mapHeaders(headers);
    assertEquals(5, map.size());
    assertEquals("discount_points", map.get(3));
    assertEquals("yield_index", map.get(4));
  }

  @Test
  void mapHeaders_alias_rate_to_noteRate() {
    String[] headers = {"rate", "lock_period", "base_price"};
    Map<Integer, String> map = HeaderDetector.mapHeaders(headers);
    assertEquals("note_rate", map.get(0));
  }

  @Test
  void mapHeaders_alias_interestRate_to_noteRate() {
    String[] headers = {"interest_rate", "lock_period", "base_price"};
    Map<Integer, String> map = HeaderDetector.mapHeaders(headers);
    assertEquals("note_rate", map.get(0));
  }

  @Test
  void mapHeaders_alias_lockPeriodDays_to_lockPeriod() {
    String[] headers = {"note_rate", "lock_period_days", "base_price"};
    Map<Integer, String> map = HeaderDetector.mapHeaders(headers);
    assertEquals("lock_period", map.get(1));
  }

  @Test
  void mapHeaders_alias_price_to_basePrice() {
    String[] headers = {"note_rate", "lock_period", "price"};
    Map<Integer, String> map = HeaderDetector.mapHeaders(headers);
    assertEquals("base_price", map.get(2));
  }

  @Test
  void mapHeaders_caseInsensitive() {
    String[] headers = {"NOTE_RATE", "LOCK_PERIOD", "BASE_PRICE"};
    Map<Integer, String> map = HeaderDetector.mapHeaders(headers);
    assertEquals(3, map.size());
    assertEquals("note_rate", map.get(0));
  }

  @Test
  void mapHeaders_missingRequiredField_throws() {
    String[] headers = {"note_rate", "lock_period"}; // missing base_price
    assertThrows(IllegalArgumentException.class, () -> HeaderDetector.mapHeaders(headers));
  }

  @Test
  void mapHeaders_headerAbsent_throws() {
    String[] headers = {"foo", "bar", "baz"}; // no recognized headers
    assertThrows(IllegalArgumentException.class, () -> HeaderDetector.mapHeaders(headers));
  }

  @Test
  void mapHeaders_emptyHeaders_throws() {
    String[] headers = new String[0];
    assertThrows(IllegalArgumentException.class, () -> HeaderDetector.mapHeaders(headers));
  }

  @Test
  void mapHeaders_whitespaceTrimmed() {
    String[] headers = {" note_rate ", " lock_period ", " base_price "};
    Map<Integer, String> map = HeaderDetector.mapHeaders(headers);
    assertEquals("note_rate", map.get(0));
  }

  @Test
  void mapHeaders_returnsImmutableMap() {
    String[] headers = {"note_rate", "lock_period", "base_price"};
    Map<Integer, String> map = HeaderDetector.mapHeaders(headers);
    assertThrows(UnsupportedOperationException.class, () -> map.put(0, "x"));
  }
}
