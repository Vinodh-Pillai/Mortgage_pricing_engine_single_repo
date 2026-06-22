package com.wcpe.ratefeed.normalization;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MappingWizardServiceTest {
  private final ObjectMapper mapper = new ObjectMapper();
  private final MappingWizardHeuristicAnalyzer analyzer = new MappingWizardHeuristicAnalyzer();
  private final NormalizationProfileRepository repository = mock(NormalizationProfileRepository.class);
  private final Map<UUID, NormalizationProfile> storedProfiles = new HashMap<>();
  private final MappingWizardService service = new MappingWizardService(analyzer, new LLMMappingProposer(analyzer), mapper, repository);

  @BeforeEach
  void setUpRepository() {
    storedProfiles.clear();
    when(repository.save(any(NormalizationProfile.class))).thenAnswer(invocation -> {
      NormalizationProfile profile = invocation.getArgument(0);
      storedProfiles.put(profile.getProfileId(), profile);
      return profile;
    });
    when(repository.findById(any(UUID.class))).thenAnswer(invocation -> Optional.ofNullable(storedProfiles.get(invocation.getArgument(0))));
    when(repository.findByTenantId(any(UUID.class))).thenAnswer(invocation -> {
      UUID tenantId = invocation.getArgument(0);
      return storedProfiles.values().stream().filter(profile -> tenantId.equals(profile.getTenantId())).toList();
    });
    when(repository.findAllPublishedByTenant(any(UUID.class))).thenAnswer(invocation -> {
      UUID tenantId = invocation.getArgument(0);
      return storedProfiles.values().stream()
          .filter(profile -> tenantId.equals(profile.getTenantId()))
          .filter(profile -> "PUBLISHED".equals(profile.getStatus()))
          .toList();
    });
  }

  @Test
  void createsDraftProfileAndPublishesThroughGovernanceLifecycle() {
    UUID tenantId = UUID.randomUUID();
    var config = mapper.createObjectNode().set("fields", mapper.createObjectNode().put("note_rate", "rate"));
    var fingerprint = mapper.createObjectNode().put("formatType", "CSV").put("investorCode", "TEST").put("productCode", "DSCR");

    MappingWizardModels.ProfileResponse created = service.createProfile(tenantId,
        new MappingWizardModels.CreateProfileRequest("Test profile", "TEST", "DSCR", config, fingerprint, mapper.createObjectNode()), "admin");

    assertEquals("DRAFT", created.status());
    assertThrows(RuntimeException.class, () -> service.approve(tenantId, created.profileId(), "approver"));
    assertEquals("SIMULATE", service.simulate(tenantId, created.profileId()).status());
    assertEquals("SIMULATE", service.getProfile(tenantId, created.profileId()).status());
    assertEquals("APPROVED", service.approve(tenantId, created.profileId(), "approver").status());
    assertEquals("PUBLISHED", service.publish(tenantId, created.profileId()).status());
  }

  @Test
  void autoMatchesPublishedProfileWithMatchingInvestorAndProduct() {
    UUID tenantId = UUID.randomUUID();
    var config = mapper.createObjectNode().set("fields", mapper.createObjectNode().put("note_rate", "rate"));
    var headers = mapper.createArrayNode().add("rate").add("price");
    var fingerprint = mapper.createObjectNode()
        .put("formatType", "CSV")
        .put("investorCode", "TEST")
        .put("productCode", "DSCR")
        .set("headerSignatures", headers);

    MappingWizardModels.ProfileResponse created = service.createProfile(tenantId,
        new MappingWizardModels.CreateProfileRequest("Test profile", "TEST", "DSCR", config, fingerprint, mapper.createObjectNode()), "admin");
    service.simulate(tenantId, created.profileId());
    service.approve(tenantId, created.profileId(), "approver");
    service.publish(tenantId, created.profileId());

    var incoming = mapper.createObjectNode()
        .put("formatType", "CSV")
        .put("investorCode", "TEST")
        .put("productCode", "DSCR")
        .set("headerSignatures", headers.deepCopy());

    MappingWizardModels.AutoMatchResponse match = service.autoMatch(tenantId, incoming);

    assertTrue(match.autoApply());
    assertEquals(100, match.matchScore());
    assertNotNull(match.profile());
  }

  @Test
  void doesNotAutoMatchWhenInvestorOrProductFingerprintValuesAreMissing() {
    UUID tenantId = UUID.randomUUID();
    var config = mapper.createObjectNode().set("fields", mapper.createObjectNode().put("note_rate", "rate"));
    var headers = mapper.createArrayNode().add("rate").add("price");
    var fingerprint = mapper.createObjectNode()
        .put("formatType", "CSV")
        .put("investorCode", "TEST")
        .put("productCode", "DSCR")
        .set("headerSignatures", headers);

    MappingWizardModels.ProfileResponse created = service.createProfile(tenantId,
        new MappingWizardModels.CreateProfileRequest("Test profile", "TEST", "DSCR", config, fingerprint, mapper.createObjectNode()), "admin");
    service.simulate(tenantId, created.profileId());
    service.approve(tenantId, created.profileId(), "approver");
    service.publish(tenantId, created.profileId());

    var incoming = mapper.createObjectNode()
        .put("formatType", "CSV")
        .set("headerSignatures", headers.deepCopy());

    MappingWizardModels.AutoMatchResponse match = service.autoMatch(tenantId, incoming);

    assertFalse(match.autoApply());
    assertEquals(60, match.matchScore());
    assertEquals("REVIEW_TOP_3", match.routingAction());
    assertEquals(1, match.reviewMatches().size());
  }

  @Test
  void routesEightyToNinetyFourMatchesToHumanValidationWithDriftEvidence() {
    UUID tenantId = UUID.randomUUID();
    var config = mapper.createObjectNode().set("fields", mapper.createObjectNode().put("note_rate", "rate"));
    var fingerprint = mapper.createObjectNode()
        .put("formatType", "CSV")
        .put("investorCode", "TEST")
        .put("productCode", "DSCR")
        .set("headerSignatures", mapper.createArrayNode().add("rate").add("price"));
    MappingWizardModels.ProfileResponse created = service.createProfile(tenantId,
        new MappingWizardModels.CreateProfileRequest("Daily profile", "TEST", "DSCR", config, fingerprint, mapper.createObjectNode()), "admin");
    service.simulate(tenantId, created.profileId());
    service.approve(tenantId, created.profileId(), "approver");
    service.publish(tenantId, created.profileId());

    var incoming = mapper.createObjectNode()
        .put("formatType", "CSV")
        .put("investorCode", "TEST")
        .put("productCode", "DSCR")
        .set("headerSignatures", mapper.createArrayNode().add("rate").add("new_column"));

    MappingWizardModels.AutoMatchResponse match = service.autoMatch(tenantId, incoming);

    assertTrue(match.autoApply());
    assertEquals(90, match.matchScore());
    assertEquals("HUMAN_VALIDATE", match.routingAction());
    assertEquals(created.profileId(), match.profile().profileId());
    assertTrue(match.driftEvidence().updateSuggested());
    assertTrue(match.driftEvidence().newSignatures().contains("new_column"));
  }

  @Test
  void routesSixtyToSeventyNineMatchesToTopThreeReviewWithoutAutoApply() {
    UUID tenantId = UUID.randomUUID();
    publishGoldenProfile(tenantId, "Review 1", "CSV", "TEST", "DSCR");
    publishGoldenProfile(tenantId, "Review 2", "CSV", "TEST", "ALT");
    publishGoldenProfile(tenantId, "Review 3", "CSV", "TEST", "JUMBO");
    publishGoldenProfile(tenantId, "Review 4", "CSV", "TEST", "OTHER");

    var incoming = mapper.createObjectNode()
        .put("formatType", "CSV")
        .put("investorCode", "TEST")
        .put("productCode", "UNKNOWN");

    MappingWizardModels.AutoMatchResponse match = service.autoMatch(tenantId, incoming);

    assertFalse(match.autoApply());
    assertNull(match.profile());
    assertEquals(60, match.matchScore());
    assertEquals("REVIEW_TOP_3", match.routingAction());
    assertEquals(3, match.reviewMatches().size());
    assertTrue(match.fallbackReason().contains("top 3"));
  }

  @Test
  void rejectsFormulaLikeMappingConfig() {
    UUID tenantId = UUID.randomUUID();
    var config = mapper.createObjectNode().put("unsafe", "=cmd()");
    var fingerprint = mapper.createObjectNode().put("formatType", "CSV");

    assertThrows(RuntimeException.class, () -> service.createProfile(tenantId,
        new MappingWizardModels.CreateProfileRequest("Unsafe", "TEST", "DSCR", config, fingerprint, mapper.createObjectNode()), "admin"));
  }

  @Test
  void localLlmTemplateBuilderUsesApprovedGoldenProfilesAndStructuredValidation() {
    UUID tenantId = UUID.randomUUID();
    publishGoldenProfile(tenantId, "Onslow v1", "EXCEL_XLSX", "ONSLOW_BAY", "DSCR_PLUS");
    publishGoldenProfile(tenantId, "Onslow v2", "EXCEL_XLSX", "ONSLOW_BAY", "DSCR_PLUS");
    publishGoldenProfile(tenantId, "FNMA v1", "CSV", "FNMA", "CONF_30YR");

    MappingWizardModels.MappingProposal proposal = service.propose(tenantId, new MappingWizardModels.ProposeRequest(
        "onslow.xlsx",
        "EXCEL_XLSX",
        List.of("Rates", "LLPA"),
        List.of("rate", "price", "lock_period_days"),
        List.of(List.of("rate", "price", "lock_period_days"), List.of("6.500", "99.125", "30")),
        MappingWizardModels.AnalysisMode.LLM,
        "ONSLOW_BAY",
        "DSCR_PLUS",
        "v1"));

    assertEquals(MappingWizardModels.AnalysisMode.LLM, proposal.mode());
    assertEquals(MappingWizardModels.Confidence.HIGH, proposal.confidence());
    assertTrue(proposal.schemaValidation().valid());
    assertTrue(proposal.fewShotExamples().size() >= 3);
    assertEquals("v1", proposal.promptMetrics().promptVersion());
    assertFalse(proposal.audit().externalProviderUsed());
    assertTrue(proposal.safetyWarnings().stream().anyMatch(w -> w.contains("hashes")));
  }

  private void publishGoldenProfile(UUID tenantId, String name, String formatType, String investorCode, String productCode) {
    var config = mapper.createObjectNode().set("fields", mapper.createObjectNode()
        .put("note_rate", "rate")
        .put("base_price", "price")
        .put("lock_period", "lock_period_days"));
    var fingerprint = mapper.createObjectNode()
        .put("formatType", formatType)
        .put("investorCode", investorCode)
        .put("productCode", productCode)
        .put("matrixDetected", true);
    MappingWizardModels.ProfileResponse created = service.createProfile(tenantId,
        new MappingWizardModels.CreateProfileRequest(name, investorCode, productCode, config, fingerprint, mapper.createObjectNode()), "admin");
    service.simulate(tenantId, created.profileId());
    service.approve(tenantId, created.profileId(), "approver");
    service.publish(tenantId, created.profileId());
  }
}
