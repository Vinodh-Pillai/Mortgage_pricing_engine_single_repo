package com.wcpe.underwriting.nonqm;

import static org.assertj.core.api.Assertions.assertThat;

import com.wcpe.underwriting.nonqm.NonQmUnderwritingApi.AusDecision;
import com.wcpe.underwriting.nonqm.NonQmUnderwritingApi.EligibilityOutcome;
import com.wcpe.underwriting.nonqm.NonQmUnderwritingApi.EligibilityStatus;
import com.wcpe.underwriting.nonqm.NonQmUnderwritingApi.GeneratedCondition;
import com.wcpe.underwriting.nonqm.NonQmUnderwritingApi.NonQmProductType;
import com.wcpe.underwriting.nonqm.NonQmUnderwritingApi.PricingContext;
import com.wcpe.underwriting.nonqm.NonQmUnderwritingApi.PricingStatus;
import com.wcpe.underwriting.nonqm.NonQmUnderwritingApi.UnderwritingRequest;
import com.wcpe.underwriting.nonqm.NonQmUnderwritingApi.UnderwritingResult;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NonQmUnderwritingApiTest {
  private final NonQmUnderwritingApi api = new NonQmUnderwritingApi();

  @Test
  void approvesDscrWithPricingAndRequiredRiskEvidence() {
    UnderwritingResult result = api.evaluate(request(NonQmProductType.DSCR, Map.of(
        "nonQm.dscr.ratio", "1.18",
        "income.rental.evidenceRef", "doc:rental:1",
        "property.taxInsurance.evidenceRef", "doc:pitia:1",
        "credit.fico", "742",
        "credit.tradelines", "3",
        "credit.housingHistory", "0x30",
        "property.appraisalRef", "appraisal:1",
        "property.condition", "C3",
        "property.type", "SFR"), Map.of()));

    assertThat(result.decision()).isEqualTo(AusDecision.APPROVE);
    assertThat(result.conditions()).extracting(GeneratedCondition::ruleId).contains("DSCR-LEASE-001");
    assertThat(result.findingsReport().pricingRefs()).contains("nonqm-rate-sheet:v1");
    assertThat(result.auditHash()).hasSize(64);
  }

  @Test
  void declinesWhenBankStatementEligibilityIsHardStopAndStillReturnsCurableConditions() {
    UnderwritingResult result = api.evaluate(request(NonQmProductType.BANK_STATEMENT, Map.of(
        "nonQm.bankStatement.monthCount", "12",
        "credit.fico", "701",
        "credit.tradelines", "2",
        "credit.housingHistory", "0x30",
        "property.appraisalRef", "appraisal:2",
        "property.condition", "C4",
        "property.type", "CONDO"), Map.of("bankStatement.requiredMonths", "24"),
        new EligibilityOutcome(EligibilityStatus.INELIGIBLE, "eligibility:nonqm:2", "MIN_DSCR_NOT_MET", List.of())));

    assertThat(result.decision()).isEqualTo(AusDecision.DECLINE);
    assertThat(result.conditions()).extracting(GeneratedCondition::ruleId)
        .contains("ELIG-HARD-STOP", "BANK-STMT-MONTHS-001", "BANK-STMT-DEPOSITS-001");
  }

  @Test
  void everyRequestedNonQmProductTypeHasProductSpecificConditionRules() {
    for (NonQmProductType type : NonQmProductType.values()) {
      UnderwritingResult result = api.evaluate(request(type, completeFactsFor(type), Map.of(
          "bankStatement.requiredMonths", "24")));

      assertThat(result.ruleSetId()).contains(type.name().toLowerCase());
      assertThat(result.conditions()).isNotEmpty();
      assertThat(result.findingsReport().ruleSetRef()).endsWith(":v1");
    }
  }

  @Test
  void missingCreditAndPropertyEvidenceReferInsteadOfInventingRiskThresholds() {
    UnderwritingResult result = api.evaluate(request(NonQmProductType.ASSET_DEPLETION, Map.of(
        "assets.verificationRef", "doc:asset:1"), Map.of()));

    assertThat(result.decision()).isEqualTo(AusDecision.REFER);
    assertThat(result.riskAssessment().creditRisk().missingFacts()).contains("credit.fico", "credit.tradelines", "credit.housingHistory");
    assertThat(result.riskAssessment().propertyRisk().missingFacts()).contains("property.appraisalRef", "property.condition", "property.type");
  }

  private static UnderwritingRequest request(NonQmProductType productType, Map<String, String> facts,
      Map<String, String> config) {
    return request(productType, facts, config,
        new EligibilityOutcome(EligibilityStatus.ELIGIBLE, "eligibility:nonqm:passed", "NON_QM_ELIGIBLE", List.of()));
  }

  private static UnderwritingRequest request(NonQmProductType productType, Map<String, String> facts,
      Map<String, String> config, EligibilityOutcome eligibility) {
    return new UnderwritingRequest("tenant-1", "scenario-" + productType.name(), productType,
        "NONQM-" + productType.name(), "INV-A", "BROKER", Instant.parse("2026-06-13T00:00:00Z"), facts,
        eligibility, pricedContext(), config, "corr-1");
  }

  private static PricingContext pricedContext() {
    return new PricingContext(PricingStatus.PRICED, "pricing-hash-1", "nonqm-rate-sheet", 1,
        "INV-PROD-1", List.of(), List.of("nonqm-rate-sheet:v1", "nonqm-margin:v1"), Map.of("ltvBand", "70_75"));
  }

  private static Map<String, String> completeFactsFor(NonQmProductType type) {
    Map<String, String> common = new java.util.LinkedHashMap<>(Map.of(
        "credit.fico", "720",
        "credit.tradelines", "3",
        "credit.housingHistory", "0x30",
        "property.appraisalRef", "appraisal:complete",
        "property.condition", "C3",
        "property.type", "SFR"));
    switch (type) {
      case DSCR -> {
        common.put("nonQm.dscr.ratio", "1.20");
        common.put("income.rental.evidenceRef", "doc:rental");
        common.put("property.taxInsurance.evidenceRef", "doc:pitia");
      }
      case BANK_STATEMENT -> {
        common.put("nonQm.bankStatement.monthCount", "12");
        common.put("income.depositAnalysisRef", "doc:deposits");
      }
      case ASSET_DEPLETION -> common.put("assets.verificationRef", "doc:assets");
      case NO_RATIO -> common.put("occupancy", "INVESTMENT");
      case FOREIGN_NATIONAL -> {
        common.put("countryTier", "TIER_1");
        common.put("creditProfile", "INTERNATIONAL_CREDIT");
      }
      case ITIN -> common.put("itinStatus", "VALID");
      case ONE099_ONLY -> {
        common.put("documentType", "1099");
        common.put("businessHistoryBand", "24_PLUS_MONTHS");
        common.put("income.1099AnalysisRef", "doc:1099");
      }
    }
    return common;
  }
}
