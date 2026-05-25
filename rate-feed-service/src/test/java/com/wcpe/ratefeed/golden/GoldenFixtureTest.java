package com.wcpe.ratefeed.golden;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.ratefeed.domain.RateFeedModels;
import com.wcpe.ratefeed.parser.RateSheetParser;
import com.wcpe.ratefeed.validation.RateSheetValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Golden fixture tests — parse and validate CSV fixtures through the full pipeline.
 * Each fixture reads from resources/golden/ and asserts expected status/rowCount.
 */
class GoldenFixtureTest {
  private final ObjectMapper mapper = new ObjectMapper();
  private final RateSheetParser parser = new RateSheetParser();
  private final RateSheetValidator validator = new RateSheetValidator();

  @TempDir
  Path tempDir;

  @BeforeEach
  void setup() {
    com.wcpe.ratefeed.domain.RequestContext.roles("RATE_FEED_UPLOAD,RATE_FEED_VIEW,RATE_FEED_ACTIVATE");
  }

  @AfterEach
  void cleanup() {
    com.wcpe.ratefeed.domain.RequestContext.clear();
  }

  @Test
  void GF01_validSheet() throws IOException {
    Fixture fix = loadFixture("GF01-valid-sheet.json");
    RateSheetParser.ParseResult result = parse(fix.csvContent());
    assertEquals(50, result.pricePoints().size(), "GF01 should parse 50 rows");
    var validation = validator.validate(result.pricePoints(), result.gridHash());
    assertEquals("VALIDATED", validation.status(), "GF01 should validate successfully");
  }

  @Test
  void GF02_duplicateRows() throws IOException {
    Fixture fix = loadFixture("GF02-duplicate-rows.json");
    RateSheetParser.ParseResult result = parse(fix.csvContent());
    var validation = validator.validate(result.pricePoints(), result.gridHash());
    assertFalse(validation.validationResult().valid(), "GF02 should fail validation due to duplicates");
    assertEquals("REJECTED", expectedStatusForValidation(validation), "GF02 should be REJECTED");
  }

  @Test
  void GF03_missingColumns() throws IOException {
    Fixture fix = loadFixture("GF03-missing-columns.json");
    assertThrows(Exception.class, () -> parse(fix.csvContent()),
        "GF03 should throw on missing required columns");
  }

  @Test
  void GF04_outOfRange() throws IOException {
    Fixture fix = loadFixture("GF04-out-of-range.json");
    RateSheetParser.ParseResult result = parse(fix.csvContent());
    var validation = validator.validate(result.pricePoints(), result.gridHash());
    assertEquals("REJECTED", expectedStatusForValidation(validation), "GF04 should be REJECTED");
  }

  @Test
  void GF05_multiLockPeriods() throws IOException {
    Fixture fix = loadFixture("GF05-multi-lock-periods.json");
    RateSheetParser.ParseResult result = parse(fix.csvContent());
    assertEquals(5, result.pricePoints().size(), "GF05 should parse 5 rows");
    var validation = validator.validate(result.pricePoints(), result.gridHash());
    assertEquals("VALIDATED", validation.status(), "GF05 should validate successfully");
  }

  @Test
  void GF06_emptyFile() throws IOException {
    Fixture fix = loadFixture("GF06-empty-file.json");
    assertThrows(Exception.class, () -> parse(fix.csvContent()),
        "GF06 should throw on empty data rows");
  }

  Fixture loadFixture(String filename) throws IOException {
    try (InputStream is = getClass().getClassLoader().getResourceAsStream("golden/" + filename)) {
      if (is == null) throw new AssertionError("Fixture not found: " + filename);
      JsonNode node = mapper.readTree(is);
      return new Fixture(
          node.get("fixtureId").asText(),
          node.get("csvContent").asText(),
          node.get("expected").get("status").asText(),
          node.get("expected").get("rowCount").asInt());
    }
  }

  RateSheetParser.ParseResult parse(String csvContent) {
    UUID sheetId = UUID.randomUUID();
    RateSheetParser.ParseContext ctx = new RateSheetParser.ParseContext(
        sheetId, UUID.randomUUID(), UUID.randomUUID(), "FIXTURE", Instant.now());
    InputStream is = new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8));
    return parser.parse(is, ctx);
  }

  String expectedStatusForValidation(RateFeedModels.ValidationResultResponse vr) {
    return vr.validationResult().valid() ? "VALIDATED" : "REJECTED";
  }

  record Fixture(String fixtureId, String csvContent, String expectedStatus, int expectedRowCount) {}
}
