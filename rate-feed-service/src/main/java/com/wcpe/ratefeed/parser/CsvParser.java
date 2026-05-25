package com.wcpe.ratefeed.parser;

import java.util.*;

/**
 * G-001: CSV tokenization and delimiter detection.
 * Supports comma, tab, semicolon, pipe.
 */
public final class CsvParser {
  private static final char[] CANDIDATES = {',', '\t', ';', '|'};

  private CsvParser() {}

  /** Scans first 50 data rows to find the delimiter with the most consistent occurrence. */
  public static char detectDelimiter(List<String> lines) {
    int scanLimit = Math.min(lines.size(), 50);
    int[] counts = new int[CANDIDATES.length];

    for (int i = 1; i < scanLimit && i < lines.size(); i++) {
      for (int c = 0; c < CANDIDATES.length; c++) {
        counts[c] += countChar(lines.get(i), CANDIDATES[c]);
      }
    }

    int best = 0;
    for (int c = 1; c < counts.length; c++) {
      if (counts[c] > counts[best]) best = c;
    }

    if (counts[best] == 0) {
      throw new IllegalArgumentException("No valid CSV delimiter detected – file may be empty or not CSV");
    }

    return CANDIDATES[best];
  }

  /** Tokenizes a single line by the given delimiter, respecting quoted fields. */
  public static String[] tokenizeLine(String line, char delimiter) {
    List<String> tokens = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    boolean inQuotes = false;

    for (int i = 0; i < line.length(); i++) {
      char ch = line.charAt(i);
      if (ch == '"') {
        inQuotes = !inQuotes;
      } else if (ch == delimiter && !inQuotes) {
        tokens.add(current.toString().trim());
        current = new StringBuilder();
      } else {
        current.append(ch);
      }
    }
    tokens.add(current.toString().trim());
    return tokens.toArray(new String[0]);
  }

  private static int countChar(String s, char c) {
    int count = 0;
    for (int i = 0; i < s.length(); i++) {
      if (s.charAt(i) == c) count++;
    }
    return count;
  }
}
