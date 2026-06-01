package com.wcpe.ratefeed.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FixtureBackedIngestionValidatorTest {
  private final FixtureBackedIngestionValidator validator = new FixtureBackedIngestionValidator();

  @Test
  void validFixtureProducesPublishableRowsUsingTemporaryIdentifiers() {
    String csv = "note_rate,lock_period,base_price\n6.500,30,101.250\n6.750,45,100.500\n";

    var result = validator.validateCsv(csv, FixtureBackedIngestionValidator.temporaryIdentifierContext());

    assertThat(result.publishable()).isTrue();
    assertThat(result.publishableRows()).hasSize(2);
    assertThat(result.quarantineReasons()).isEmpty();
    assertThat(result.gridHash()).isNotBlank();
  }

  @Test
  void invalidFixtureQuarantinesRowsWithExplicitReasons() {
    String csv = "note_rate,lock_period,base_price\n6.500,30,101.250\n6.500,30,100.500\n";

    var result = validator.validateCsv(csv, FixtureBackedIngestionValidator.temporaryIdentifierContext());

    assertThat(result.publishable()).isFalse();
    assertThat(result.quarantineReasons()).anyMatch(reason -> reason.contains("DUPLICATE"));
  }
}
