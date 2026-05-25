package com.wcpe.ratefeed.parser;

import com.wcpe.ratefeed.domain.RateFeedModels.RatePricePoint;

import java.io.*;
import java.math.BigDecimal;
import java.math.MathContext;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * G-001: CSV → RatePricePoint pipeline.
 *
 * Steps:
 *   1. CsvParser.detectDelimiter + tokenizeLine
 *   2. HeaderDetector.mapHeaders
 *   3. TypeCoercer.coerce per-cell (formula rejection)
 *   4. Hashing.gridHash for G-008 deterministic hash
 */
public class RateSheetParser {

  /**
   * Parses a CSV stream into RatePricePoint list + deterministic grid hash.
   *
   * @param csv input stream of the CSV file
   * @param context contextual data (sheetId, investorId, channelId, productCode, effectiveAt)
   * @return structured parse result
   * @throws IllegalArgumentException on formula injection, type coercion failure, or header mismatch
   */
  public ParseResult parse(InputStream csv, ParseContext context) {
    List<String> lines;
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(csv, StandardCharsets.UTF_8))) {
      lines = reader.lines().collect(Collectors.toList());
    } catch (IOException e) {
      throw new RuntimeException("Failed to read CSV input stream", e);
    }

    if (lines.isEmpty()) {
      throw new IllegalArgumentException("CSV file is empty");
    }

    // 1. Delimiter detection
    char delimiter = CsvParser.detectDelimiter(lines);

    // 2. Header mapping
    String[] headerTokens = CsvParser.tokenizeLine(lines.get(0), delimiter);
    Map<Integer, String> headerMap = HeaderDetector.mapHeaders(headerTokens);

    // 3. Parse data rows
    List<RatePricePoint> pricePoints = new ArrayList<>();
    List<ParserWarning> warnings = new ArrayList<>();
    int gridPosition = 0;

    for (int lineIndex = 1; lineIndex < lines.size(); lineIndex++) {
      String line = lines.get(lineIndex).trim();

      // Empty row handling
      if (line.isEmpty()) {
        warnings.add(new ParserWarning("EMPTY_ROW", "Line " + (lineIndex + 1) + " is empty, skipped"));
        continue;
      }

      String[] cells = CsvParser.tokenizeLine(line, delimiter);
      gridPosition++;

      try {
        RatePricePoint point = cellsToRatePricePoint(cells, headerMap, gridPosition, context);
        pricePoints.add(point);
      } catch (ParserRowException e) {
        // Non-fatal parse warnings (bad numeric, etc.), skip row
        warnings.add(new ParserWarning(e.code, e.getMessage() + " at line " + (lineIndex + 1)));
      } // formula injection re-throws
    }

    // G-008: deterministic grid hash
    String gridHash = com.wcpe.ratefeed.domain.Hashing.gridHash(
        new com.fasterxml.jackson.databind.ObjectMapper(), pricePoints);

    return new ParseResult(pricePoints, gridHash, gridPosition, warnings);
  }

  private RatePricePoint cellsToRatePricePoint(
      String[] cells,
      Map<Integer, String> headerMap,
      int gridPosition,
      ParseContext context) {

    String noteRateRaw = cellValue("note_rate", cells, headerMap);
    String lockPeriodRaw = cellValue("lock_period", cells, headerMap);
    String basePriceRaw = cellValue("base_price", cells, headerMap);
    String discountPointsRaw = cellValue("discount_points", cells, headerMap);
    String yieldIndexRaw = cellValue("yield_index", cells, headerMap);

    // Formula injection check – all cells
    rejectFormulaCell("note_rate", noteRateRaw);
    rejectFormulaCell("lock_period", lockPeriodRaw);
    rejectFormulaCell("base_price", basePriceRaw);
    if (discountPointsRaw != null) rejectFormulaCell("discount_points", discountPointsRaw);
    if (yieldIndexRaw != null) rejectFormulaCell("yield_index", yieldIndexRaw);

    // Coerce required fields
    BigDecimal noteRate = TypeCoercer.coerceRate(noteRateRaw, gridPosition);
    int lockPeriod = TypeCoercer.coerceLockPeriod(lockPeriodRaw, gridPosition);
    BigDecimal basePrice = TypeCoercer.coerceBasePrice(basePriceRaw, gridPosition);

    // Coerce optional fields
    BigDecimal discountPoints = TypeCoercer.coerceNullableOptionalDiscountPoints(discountPointsRaw, gridPosition);
    BigDecimal yieldIndex = TypeCoercer.coerceNullableYieldIndex(yieldIndexRaw, gridPosition);

    return new RatePricePoint(
        context.sheetId(),
        noteRate,
        lockPeriod,
        basePrice,
        discountPoints,
        yieldIndex,
        gridPosition
    );
  }

  private String cellValue(String canonical, String[] cells, Map<Integer, String> headerMap) {
    for (Map.Entry<Integer, String> entry : headerMap.entrySet()) {
      if (entry.getValue().equals(canonical)) {
        int idx = entry.getKey();
        if (idx < cells.length) {
          return cells[idx].trim();
        }
      }
    }
    return null;
  }

  private void rejectFormulaCell(String field, String value) {
    if (value == null || value.isBlank()) return;
    String trimmed = value.stripLeading();
    if (trimmed.isEmpty()) return;
    char first = trimmed.charAt(0);
    if (first == '=' || first == '+' || first == '-' || first == '@' ||
        first == '\t' || first == '\r' || first == '\n') {
      throw new IllegalArgumentException("FORMULA_INJECTION_RISK: " + field + " cell starts with suspicious character '" + first + "'");
    }
  }

  // ── Public records ──────────────────────────────────────────────────────────

  public record ParseResult(
      List<RatePricePoint> pricePoints,
      String gridHash,
      int rowCount,
      List<ParserWarning> warnings
  ) {}

  public record ParseContext(
      UUID sheetId,
      UUID investorId,
      UUID channelId,
      String productCode,
      Instant effectiveAt
  ) {}

  public record ParserWarning(String code, String message) {}

  /** Non-fatal parse error for skipping rows. */
  private static class ParserRowException extends RuntimeException {
    final String code;
    ParserRowException(String code, String message) {
      super(message);
      this.code = code;
    }
  }
}
