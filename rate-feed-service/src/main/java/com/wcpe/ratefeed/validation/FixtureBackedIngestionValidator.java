package com.wcpe.ratefeed.validation;

import com.wcpe.ratefeed.domain.RateFeedModels.RatePricePoint;
import com.wcpe.ratefeed.parser.RateSheetParser;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class FixtureBackedIngestionValidator {
  private final RateSheetParser parser = new RateSheetParser();
  private final RateSheetValidator validator = new RateSheetValidator();

  public FixtureIngestionResult validateCsv(String csv, RateSheetParser.ParseContext context) {
    try {
      RateSheetParser.ParseResult parsed = parser.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), context);
      var validation = validator.validate(parsed.pricePoints(), parsed.gridHash());
      if (!validation.validationResult().valid()) {
        return new FixtureIngestionResult(List.of(), validation.validationResult().errors().stream().map(e -> e.code() + ":" + e.message()).toList(), parsed.gridHash());
      }
      return new FixtureIngestionResult(parsed.pricePoints(), List.of(), parsed.gridHash());
    } catch (RuntimeException ex) {
      return new FixtureIngestionResult(List.of(), List.of(ex.getMessage()), null);
    }
  }

  public static RateSheetParser.ParseContext temporaryIdentifierContext() {
    return new RateSheetParser.ParseContext(
        UUID.fromString("11111111-1111-1111-1111-111111111111"),
        UUID.fromString("22222222-2222-2222-2222-222222222222"),
        UUID.fromString("33333333-3333-3333-3333-333333333333"),
        "TEMP-CONV30",
        Instant.parse("2026-01-01T00:00:00Z"));
  }

  public record FixtureIngestionResult(List<RatePricePoint> publishableRows, List<String> quarantineReasons, String gridHash) {
    public boolean publishable() {
      return !publishableRows.isEmpty() && quarantineReasons.isEmpty();
    }
  }
}
