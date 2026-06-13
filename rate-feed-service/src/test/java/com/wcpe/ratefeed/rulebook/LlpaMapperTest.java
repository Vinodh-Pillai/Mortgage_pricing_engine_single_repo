package com.wcpe.ratefeed.rulebook;

import static org.assertj.core.api.Assertions.assertThat;

import com.wcpe.ratefeed.rulebook.LlpaGridToRuleBookMapper.MappingConfig;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LlpaMapperTest {
  private final LlpaGridToRuleBookMapper mapper = new LlpaGridToRuleBookMapper();
  private final MappingConfig config = new MappingConfig(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
      UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"), "FNMA", "CONVENTIONAL", "RETAIL", "BPS_DELTA",
      Instant.parse("2026-06-01T00:00:00Z"), "2026.06", "unit-test");

  @Test
  void ficoLtvGridToRules() throws Exception {
    var draft = mapper.mapToRuleBook(fixture("fnma-sample-grid.csv"), config);
    assertThat(draft.businessKey()).isEqualTo("FNMA_LLPA_2026_06");
    assertThat(draft.rules()).hasSize(5);
    var caRule = draft.rules().stream().filter(rule -> rule.sourceRef().equals("llpa-grid-row:2")).findFirst().orElseThrow();
    assertThat(caRule.output().type()).isEqualTo("BPS_DELTA");
    assertThat(caRule.output().configuredAmount()).isEqualByComparingTo("75.0");
    assertThat(caRule.conditions()).extracting("dimension").contains("ficoBandKey", "ltvBandKey", "loanPurpose", "propertyType", "occupancy", "units", "state");
  }

  @Test
  void cashOutAddOnRules() throws Exception {
    var draft = mapper.mapToRuleBook(fixture("fnma-sample-grid.csv"), config);
    assertThat(draft.rules()).anySatisfy(rule -> {
      assertThat(rule.exclusivityGroup()).isEqualTo("FNMA_LLPA_CASH_OUT");
      assertThat(rule.conditions()).anySatisfy(condition -> assertThat(condition.configuredValues()).contains("CASH_OUT_REFI"));
    });
  }

  @Test
  void propertyTypeAddOnRules() throws Exception {
    var draft = mapper.mapToRuleBook(fixture("fnma-sample-grid.csv"), config);
    assertThat(draft.rules()).anySatisfy(rule -> {
      assertThat(rule.exclusivityGroup()).isEqualTo("FNMA_LLPA_PROPERTY_TYPE");
      assertThat(rule.conditions()).anySatisfy(condition -> assertThat(condition.configuredValues()).contains("CONDO"));
    });
  }

  @Test
  void stateSpecificRules() throws Exception {
    var draft = mapper.mapToRuleBook(fixture("fhlmc-sample-grid.csv"), new MappingConfig(config.tenantId(), config.rateSheetId(), "FHLMC", "CONVENTIONAL", "RETAIL", "BPS_DELTA", config.effectiveAt(), "2026.06", "unit-test"));
    assertThat(draft.rules()).anySatisfy(rule -> assertThat(rule.conditions()).anySatisfy(condition -> {
      assertThat(condition.dimension()).isEqualTo("county");
      assertThat(condition.configuredValues()).contains("TRAVIS");
    }));
  }

  @Test
  void priorityBySpecificity() throws Exception {
    var draft = mapper.mapToRuleBook(fixture("fhlmc-sample-grid.csv"), config);
    assertThat(draft.rules()).allSatisfy(rule -> assertThat(rule.priority()).isEqualTo(rule.conditions().size()));
  }

  @Test
  void exclusivityGroups() throws Exception {
    var draft = mapper.mapToRuleBook(fixture("fnma-sample-grid.csv"), config);
    assertThat(draft.rules()).extracting("exclusivityGroup").contains("FNMA_LLPA_CORE", "FNMA_LLPA_CASH_OUT", "FNMA_LLPA_PROPERTY_TYPE", "FNMA_LLPA_UNITS");
  }

  private String fixture(String name) throws Exception {
    try (var stream = getClass().getResourceAsStream("/llpa/" + name)) {
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
