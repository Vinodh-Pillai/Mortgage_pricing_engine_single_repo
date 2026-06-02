package com.wcpe.ratefeed.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.wcpe.ratefeed.parser.RateSheetParser;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FixtureBackedIngestionValidatorTest {
  private final FixtureBackedIngestionValidator validator = new FixtureBackedIngestionValidator();

  @Test
  void validFixtureProducesPublishableRowsUsingTemporaryIdentifiers() {
    String csv = "note_rate,lock_period,base_price\n6.500,30,101.250\n6.750,45,100.500\n";

    var result = validator.validateCsv(csv, temporaryIdentifierContext());

    assertThat(result.publishable()).isTrue();
    assertThat(result.publishableRows()).hasSize(2);
    assertThat(result.quarantineReasons()).isEmpty();
    assertThat(result.gridHash()).isNotBlank();
  }

  @Test
  void invalidFixtureQuarantinesRowsWithExplicitReasons() {
    String csv = "note_rate,lock_period,base_price\n6.500,30,101.250\n6.500,30,100.500\n";

    var result = validator.validateCsv(csv, temporaryIdentifierContext());

    assertThat(result.publishable()).isFalse();
    assertThat(result.quarantineReasons()).anyMatch(reason -> reason.contains("DUPLICATE"));
  }

  private static RateSheetParser.ParseContext temporaryIdentifierContext() {
    return new RateSheetParser.ParseContext(
        UUID.fromString("11111111-1111-1111-1111-111111111111"),
        UUID.fromString("22222222-2222-2222-2222-222222222222"),
        UUID.fromString("33333333-3333-3333-3333-333333333333"),
        "TEMP-CONV30",
        Instant.parse("2026-01-01T00:00:00Z"));
  }
}
