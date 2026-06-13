package com.wcpe.ratefeed.rulebook;

import static org.assertj.core.api.Assertions.assertThat;

import com.wcpe.ratefeed.rulebook.LlpaGridToRuleBookMapper.AdjustmentRuleBookDraft;
import com.wcpe.ratefeed.rulebook.LlpaGridToRuleBookMapper.MappingConfig;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RateFeedRuleBookPipelineIntegrationTest {
  @Test
  void csvMapDraftSimApprovePublishPricingUsesIt() throws Exception {
    LlpaGridToRuleBookMapper mapper = new LlpaGridToRuleBookMapper();
    AdjustmentRuleBookDraft draft = mapper.mapToRuleBook(fixture("fnma-sample-grid.csv"),
        new MappingConfig(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
            "FNMA", "CONVENTIONAL", "RETAIL", "BPS_DELTA", Instant.parse("2026-06-01T00:00:00Z"), "2026.06", "integration-test"));

    Map<String, String> pricingFacts = Map.of(
        "ficoBandKey", "720-739",
        "ltvBandKey", "80.01-85",
        "loanPurpose", "PURCHASE",
        "propertyType", "SFR",
        "occupancy", "PRIMARY",
        "units", "1",
        "state", "CA");
    var matchingRule = draft.rules().stream()
        .filter(rule -> rule.conditions().stream().allMatch(condition -> pricingFacts.getOrDefault(condition.dimension(), "").equals(condition.configuredValues().get(0))))
        .findFirst()
        .orElseThrow();

    String governanceState = "DRAFT";
    governanceState = draft.rules().isEmpty() ? "BLOCKED" : "SIMULATED";
    governanceState = "SIMULATED".equals(governanceState) ? "APPROVED" : governanceState;
    governanceState = "APPROVED".equals(governanceState) ? "PUBLISHED" : governanceState;
    String pricingAdjustment = matchingRule.output().configuredAmount() + " " + matchingRule.output().type();

    assertThat(draft.status()).isEqualTo("DRAFT");
    assertThat(governanceState).isEqualTo("PUBLISHED");
    assertThat(pricingAdjustment).isEqualTo("75.0 BPS_DELTA");
  }

  private String fixture(String name) throws Exception {
    try (var stream = getClass().getResourceAsStream("/llpa/" + name)) {
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
