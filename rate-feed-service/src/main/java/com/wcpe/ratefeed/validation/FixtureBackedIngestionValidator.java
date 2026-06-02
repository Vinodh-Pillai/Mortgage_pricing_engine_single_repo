package com.wcpe.ratefeed.validation;

import com.wcpe.ratefeed.domain.RateFeedModels.RatePricePoint;
import com.wcpe.ratefeed.parser.RateSheetParser;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

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

  public record FixtureIngestionResult(List<RatePricePoint> publishableRows, List<String> quarantineReasons, String gridHash) {
    public boolean publishable() {
      return !publishableRows.isEmpty() && quarantineReasons.isEmpty();
    }
  }
}
