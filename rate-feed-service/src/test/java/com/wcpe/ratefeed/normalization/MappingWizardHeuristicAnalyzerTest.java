package com.wcpe.ratefeed.normalization;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MappingWizardHeuristicAnalyzerTest {
  private final MappingWizardHeuristicAnalyzer analyzer = new MappingWizardHeuristicAnalyzer();

  @Test
  void csvAliasesProduceMediumConfidenceMappingWithoutExternalLlm() throws Exception {
    String csv = "rate,price,lock_period_days,custom_column\n6.500,99.125,30,ignored\n";

    MappingWizardModels.MappingProposal proposal = analyzer.analyze("sample.csv", csv.getBytes(StandardCharsets.UTF_8));

    assertEquals(MappingWizardModels.Confidence.MEDIUM, proposal.confidence());
    assertEquals("CSV", proposal.formatType());
    assertFalse(proposal.audit().externalProviderUsed());
    assertEquals(3, proposal.mappings().size());
    assertEquals("note_rate", proposal.mappings().get(0).canonicalField());
    assertEquals("base_price", proposal.mappings().get(1).canonicalField());
    assertEquals("lock_period", proposal.mappings().get(2).canonicalField());
    assertEquals(0.60, proposal.mappings().get(0).confidenceScore());
    assertFalse(proposal.mappings().get(0).reasoning().isBlank());
    assertFalse(proposal.mappings().get(0).alternatives().isEmpty());
    assertEquals("heuristic-v1", proposal.promptMetrics().promptVersion());
  }

  @Test
  void formulaLikeCsvCellsAreFlaggedForSafetyReview() throws Exception {
    String csv = "rate,price,lock_period_days\n=cmd(),99.125,30\n";

    MappingWizardModels.MappingProposal proposal = analyzer.analyze("sample.csv", csv.getBytes(StandardCharsets.UTF_8));

    assertFalse(proposal.safetyWarnings().isEmpty());
  }
}
