package com.wcpe.scenario.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScenarioIntakeMetadataCatalogTest {
  @Test
  void metadataCoversRichScenarioFactsAndReplayReferencesWithoutPricingRules() {
    ScenarioIntakeMetadata metadata = ScenarioIntakeMetadataCatalog.metadata(
        UUID.fromString("018fa4f0-1a4f-7e99-a02d-1b0100010001"), "corr-test");

    assertThat(metadata.fieldGroups()).extracting(ScenarioIntakeFieldGroup::groupId)
        .containsExactly("scenario-identity", "borrower-loan-property", "income-assets");
    assertThat(metadata.fieldGroups()).flatExtracting(ScenarioIntakeFieldGroup::fields)
        .extracting(ScenarioIntakeField::fieldId)
        .contains("scenarioName", "channel", "externalLoanId", "borrowerCreditStatus", "loanAmount",
            "propertyState", "monthlyIncome", "liquidAssets");
    assertThat(metadata.decisionControls()).contains("Carry audit package and replay hash references with intake state.");
    assertThat(metadata.auditPackageId()).isEqualTo("created-after-draft-scenario");
    assertThat(metadata.replayHashRef()).isEqualTo("computed-after-draft-scenario");
    assertThat(metadata.decisionControls()).allSatisfy(control ->
        assertThat(control).doesNotContainIgnoringCase("rate table").doesNotContainIgnoringCase("pricing formula"));
  }
}
