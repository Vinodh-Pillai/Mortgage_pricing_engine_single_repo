package com.wcpe.ratefeed.parser;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CSV parsing tests — maps to actual CsvParser API:
 * detectDelimiter(List<String>), tokenizeLine(String, char)
 */
class CsvParserNewTest {

  @Test
  void detectDelimiter_comma_preferred() {
    String csv = "note_rate,lock_period,base_price\n"
        + "6.125,30,108.5\n"
        + "7.0,45,110.0";
    List<String> lines = new ArrayList<>();
    for (String s : csv.split("\n")) lines.add(s);
    assertEquals(',', CsvParser.detectDelimiter(lines));
  }

  @Test
  void detectDelimiter_tab_preferred() {
    String csv = "note_rate\tlock_period\tbase_price\n"
        + "6.125\t30\t108.5\n"
        + "7.0\t45\t110.0";
    List<String> lines = new ArrayList<>();
    for (String s : csv.split("\n")) lines.add(s);
    assertEquals('\t', CsvParser.detectDelimiter(lines));
  }

  @Test
  void detectDelimiter_semicolon_preferred() {
    String csv = "note_rate;lock_period;base_price\n"
        + "6.125;30;108.5";
    List<String> lines = List.of("note_rate;lock_period;base_price", "6.125;30;108.5");
    assertEquals(';', CsvParser.detectDelimiter(lines));
  }

  @Test
  void detectDelimiter_pipe_preferred() {
    String csv = "note_rate|lock_period|base_price\n"
        + "6.125|30|108.5";
    List<String> lines = List.of("note_rate|lock_period|base_price", "6.125|30|108.5");
    assertEquals('|', CsvParser.detectDelimiter(lines));
  }

  @Test
  void detectDelimiter_noDelimiters_throws() {
    List<String> lines = List.of("abcdefg", "hijklmn");
    assertThrows(IllegalArgumentException.class, () -> CsvParser.detectDelimiter(lines));
  }

  @Test
  void detectDelimiter_emptyList_throws() {
    assertThrows(IllegalArgumentException.class, () -> CsvParser.detectDelimiter(List.of()));
  }

  @Test
  void tokenizeLine_comma_basic() {
    String[] tokens = CsvParser.tokenizeLine("a,b,c", ',');
    assertArrayEquals(new String[]{"a", "b", "c"}, tokens);
  }

  @Test
  void tokenizeLine_quotedField_withCommas() {
    String[] tokens = CsvParser.tokenizeLine("\"a,b\",c", ',');
    assertEquals(2, tokens.length);
    assertEquals("a,b", tokens[0].trim());
  }

  @Test
  void tokenizeLine_emptyString() {
    String[] tokens = CsvParser.tokenizeLine("", ',');
    assertEquals(1, tokens.length);
    assertEquals("", tokens[0]);
  }

  @Test
  void tokenizeLine_leadingDelimiter() {
    String[] tokens = CsvParser.tokenizeLine(",a,b", ',');
    assertEquals(3, tokens.length);
    assertEquals("", tokens[0]);
  }

  @Test
  void tokenizeLine_trailingDelimiter() {
    String[] tokens = CsvParser.tokenizeLine("a,b,", ',');
    assertEquals(3, tokens.length);
    assertEquals("", tokens[2]);
  }
}
