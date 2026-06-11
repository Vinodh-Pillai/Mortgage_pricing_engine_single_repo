package com.wcpe.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RuleBuilderServiceTest {
  private static final String TENANT_ONE = "11111111-1111-1111-1111-111111111111";

  private final RuleBuilderService service =
      new RuleBuilderService(Clock.fixed(Instant.parse("2026-06-04T12:05:00Z"), ZoneOffset.UTC));

  @Test
  void savesMetadataDrivenRuleSetDraftWithAuditAndOutboxEvidence() {
    GovernanceValidationResult<RuleBuilderDraftResult> result = service.saveDraft(draftCommand("idem-rule-builder-1", ruleSet()));

    assertTrue(result.valid());
    RuleBuilderDraftResult draft = result.value().orElseThrow();
    assertEquals("rule-set-tenant-configured", draft.ruleSetId());
    assertEquals("DRAFT", draft.status());
    assertEquals(List.of("metadata-rule-builder-2026-06"), draft.metadataVersionRefs());
    assertEquals(64, draft.payloadHash().length());
    assertTrue(draft.validationMessages().isEmpty());
    assertEquals("corr-PII-12-S05", draft.correlationId());
    assertEquals(RuleBuilderService.DRAFT_EVENT_TYPE, service.outboxEvents().get(0).eventType());
    assertEquals(RuleBuilderService.AUDIT_ACTION, service.auditRecords().get(0).action());
  }

  @Test
  void failsClosedWhenRuleReferencesMissingMetadataOrReasonCodes() {
    RuleBuilderRuleSet invalidRuleSet =
        new RuleBuilderRuleSet(
            "rule-set-tenant-configured",
            "2026.06",
            "Tenant configured rules",
            "pricing-admin",
            "PRIORITY_ORDER",
            List.of(
                new RuleBuilderRule(
                    "rule-1",
                    "Rule with missing metadata refs",
                    true,
                    10,
                    List.of("group-1"),
                    List.of(new RuleBuilderCondition("condition-1", "unknown-dimension", "metadata-equals", "value-source-catalog")),
                    List.of(new RuleBuilderAction("action-1", "metadata-adjustment", "", "precision-configured", "rounding-configured")),
                    false)),
            List.of("metadata-rule-builder-2026-06"),
            List.of());

    GovernanceValidationResult<RuleBuilderDraftResult> result = service.saveDraft(draftCommand("idem-rule-builder-1", invalidRuleSet));

    assertFalse(result.valid());
    assertEquals("POLICY_NOT_SATISFIED: DIMENSION_METADATA_MISSING", result.error().orElseThrow());
    assertTrue(service.outboxEvents().isEmpty());
    assertTrue(service.auditRecords().isEmpty());
  }

  @Test
  void simulationFailsClosedForMissingFactsAndReturnsLedgerFromMetadataRefs() {
    GovernanceValidationResult<RuleBuilderSimulationResult> result =
        service.simulate(
            new RuleBuilderSimulationCommand(
                TENANT_ONE,
                "idem-sim-1",
                "admin-editor-1",
                List.of(RuleBuilderService.SIMULATE_PERMISSION),
                metadata(),
                ruleSet(),
                Map.of(),
                "draft-version-1",
                "corr-PII-12-S05"));

    assertTrue(result.valid());
    RuleBuilderSimulationResult simulation = result.value().orElseThrow();
    assertEquals("BLOCKED", simulation.status());
    assertEquals("UNKNOWN_FACT_FAIL_CLOSED", simulation.validationMessages().get(0).code());
    assertEquals("metadata-adjustment", simulation.ledger().get(0).actionOutputRef());
    assertEquals("dimension-configured", simulation.ledger().get(0).sourceDimensionRef());
    assertEquals("precision-configured", simulation.ledger().get(0).precisionRef());
    assertEquals("rounding-configured", simulation.ledger().get(0).roundingRef());
    assertEquals("reason-configured", simulation.ledger().get(0).reasonCodeRef());
    assertEquals(RuleBuilderService.SIMULATION_EVENT_TYPE, service.outboxEvents().get(0).eventType());
  }

  @Test
  void sameIdempotencyKeyReplaysAndChangedRequestConflicts() {
    RuleBuilderDraftCommand original = draftCommand("idem-rule-builder-1", ruleSet());
    RuleBuilderDraftResult first = service.saveDraft(original).value().orElseThrow();
    RuleBuilderDraftResult replay = service.saveDraft(original).value().orElseThrow();
    GovernanceValidationResult<RuleBuilderDraftResult> conflict =
        service.saveDraft(
            draftCommand(
                "idem-rule-builder-1",
                new RuleBuilderRuleSet(
                    "rule-set-tenant-configured",
                    "2026.06",
                    "Changed name",
                    "pricing-admin",
                    "PRIORITY_ORDER",
                    ruleSet().rules(),
                    List.of("metadata-rule-builder-2026-06"),
                    List.of("reason-configured"))));

    assertEquals(first, replay);
    assertFalse(conflict.valid());
    assertEquals("IDEMPOTENCY_CONFLICT", conflict.error().orElseThrow());
    assertEquals(1, service.outboxEvents().size());
  }

  @Test
  void describesCustomFieldsWithTypedOperatorsAndDecisionQualityRequirement() {
    GovernanceValidationResult<List<RuleBuilderCustomFieldDescriptor>> result = service.describeCustomFields(metadata());

    assertTrue(result.valid());
    RuleBuilderCustomFieldDescriptor descriptor = result.value().orElseThrow().get(0);
    assertEquals("dimension-configured", descriptor.stableId());
    assertEquals("catalog-ref", descriptor.dataType());
    assertEquals(List.of("metadata-equals"), descriptor.allowedOperators());
    assertEquals(List.of("value-source-catalog"), descriptor.valueSources());
    assertEquals("CONFIRMED_OR_ESTIMATED", descriptor.decisionQualityRequirement());
    assertEquals("metadata-rule-builder-2026-06", descriptor.versionRef());
    assertTrue(descriptor.validationMessages().isEmpty());
  }

  @Test
  void describesMortgageProductPricingRequirementMetadataDescriptors() {
    GovernanceValidationResult<List<RuleBuilderCustomFieldDescriptor>> result = service.describeCustomFields(mortgagePricingMetadata());

    assertTrue(result.valid());
    RuleBuilderCustomFieldDescriptor descriptor = result.value().orElseThrow().get(0);
    assertEquals("product.requirement.code", descriptor.fieldId());
    assertEquals("Mortgage product requirement code", descriptor.displayKey());
    assertEquals("product", descriptor.domainCategory());
    assertEquals("catalog-ref", descriptor.dataType());
    assertEquals("value-source-product-catalog", descriptor.valueSource());
    assertEquals("product-catalog-service", descriptor.ownerService());
    assertEquals(List.of("metadata-equals"), descriptor.allowedOperators());
    assertTrue(descriptor.decisionQualityRequired());
    assertEquals("rule-builder.product-requirement.required", descriptor.validationMessageRefs().get(0));
    assertTrue(descriptor.referenceEvidenceRefs().isEmpty());
    assertTrue(descriptor.validationMessages().isEmpty());
    assertEquals("metadata-mortgage-pricing-2026-06", descriptor.versionRef());
  }

  @Test
  void ruleReferencingDescriptorWithoutSourceOrVersionMetadataFailsClosed() {
    RuleBuilderMetadata missingSourceMetadata =
        new RuleBuilderMetadata(
            "metadata-mortgage-pricing-2026-06",
            Map.of(
                "product.requirement.code",
                new RuleBuilderDimensionMetadata(
                    "product.requirement.code",
                    "Mortgage product requirement code",
                    List.of("metadata-equals"),
                    List.of(),
                    "product",
                    "product-catalog-service",
                    true,
                    "",
                    "",
                    "effective-date",
                    List.of("rule-builder.product-requirement.required"),
                    List.of())),
            Map.of("metadata-equals", new RuleBuilderOperatorMetadata("metadata-equals", "Equals", "catalog-ref")),
            Map.of("metadata-adjustment", new RuleBuilderActionMetadata("metadata-adjustment", "Tenant-configured action", true, "unit-configured")),
            List.of("PRIORITY_ORDER"),
            List.of("precision-configured"),
            List.of("rounding-configured"),
            List.of("reason-configured"),
            false);

    GovernanceValidationResult<RuleBuilderDraftResult> missingSource = service.saveDraft(draftCommand("idem-missing-source", missingSourceMetadata, mortgagePricingRuleSet()));

    assertFalse(missingSource.valid());
    assertEquals("POLICY_NOT_SATISFIED: VALUE_SOURCE_METADATA_MISSING", missingSource.error().orElseThrow());

    RuleBuilderMetadata missingVersionMetadata =
        new RuleBuilderMetadata(
            "",
            mortgagePricingMetadata().dimensions(),
            mortgagePricingMetadata().operators(),
            mortgagePricingMetadata().actions(),
            mortgagePricingMetadata().precedenceStrategies(),
            mortgagePricingMetadata().precisionRefs(),
            mortgagePricingMetadata().roundingRefs(),
            mortgagePricingMetadata().reasonCodeRefs(),
            false);

    GovernanceValidationResult<RuleBuilderDraftResult> missingVersion = service.saveDraft(draftCommand("idem-missing-version", missingVersionMetadata, mortgagePricingRuleSet()));

    assertFalse(missingVersion.valid());
    assertEquals("POLICY_NOT_SATISFIED: RULE_METADATA_MISSING", missingVersion.error().orElseThrow());
  }

  @Test
  void dynamicRuleEvaluationFailsClosedForUnknownOrConflictingRequiredFacts() {
    GovernanceValidationResult<RuleBuilderDynamicEvaluationResult> unknownResult =
        service.evaluateDynamicRules(
            dynamicCommand(
                Map.of(
                    "dimension-configured",
                    new RuleBuilderTypedFact("CONFIGURED_VALUE", "fact-1", RuleBuilderFactQuality.UNKNOWN, "scenario", "precision-configured"))));

    assertTrue(unknownResult.valid());
    RuleBuilderDynamicEvaluationResult unknown = unknownResult.value().orElseThrow();
    assertEquals("BLOCKED", unknown.status());
    assertEquals("UNKNOWN_FACT_FAIL_CLOSED", unknown.validationMessages().get(0).code());
    assertEquals(List.of("rule-1"), unknown.skippedRuleIds());
    assertTrue(unknown.actionOutputs().isEmpty());

    GovernanceValidationResult<RuleBuilderDynamicEvaluationResult> conflictingResult =
        service.evaluateDynamicRules(
            dynamicCommand(
                Map.of(
                    "dimension-configured",
                    new RuleBuilderTypedFact("CONFIGURED_VALUE", "fact-1", RuleBuilderFactQuality.CONFLICTING, "scenario", "precision-configured"))));

    assertEquals("CONFLICTING_FACT_FAIL_CLOSED", conflictingResult.value().orElseThrow().validationMessages().get(0).code());
  }

  @Test
  void dynamicRuleEvaluationRecordsMatchedRulesActionOutputsFactRefsAndEvidenceHash() {
    GovernanceValidationResult<RuleBuilderDynamicEvaluationResult> result =
        service.evaluateDynamicRules(
            dynamicCommand(
                Map.of(
                    "dimension-configured",
                    new RuleBuilderTypedFact("CONFIGURED_VALUE", "fact-tenant-1", RuleBuilderFactQuality.CONFIRMED, "scenario", "precision-configured"))));

    assertTrue(result.valid());
    RuleBuilderDynamicEvaluationResult evaluation = result.value().orElseThrow();
    assertEquals("PASSED", evaluation.status());
    assertEquals(64, evaluation.resultHash().length());
    assertEquals(evaluation.resultHash(), evaluation.evidenceHash());
    assertEquals(List.of("rule-1"), evaluation.matchedRuleIds());
    assertTrue(evaluation.skippedRuleIds().isEmpty());
    RuleBuilderDynamicActionOutput output = evaluation.actionOutputs().get(0);
    assertEquals("metadata-adjustment", output.actionOutputRef());
    assertEquals("draft-version-1", output.ruleVersionRef());
    assertEquals(List.of("fact-tenant-1"), output.factRefs());
    assertEquals("precision-configured", output.precisionRef());
    assertEquals("rounding-configured", output.roundingRef());
    assertEquals("reason-configured", output.reasonCodeRef());
    assertEquals("unit-configured", output.unit());
    assertEquals("metadata-adjustment", output.outputType());
    assertEquals("admin-editor-1", output.sourceService());
    assertTrue(output.eligibleForCalculationLedger());
    assertEquals(36, output.ledgerStepRef().length());
    assertEquals(36, output.auditRef().length());
  }

  @Test
  void publishedRuleVersionContractReturnsRuleRefsSourceFactsActionsReasonsAndReplayEvidence() {
    GovernanceValidationResult<CustomRuleEvaluationResultV1> result =
        service.evaluatePublishedRuleVersion(consumerRequest(confirmedFact(), "draft-version-1", metadata(), dynamicRuleSet()));

    assertTrue(result.valid());
    CustomRuleEvaluationResultV1 evaluation = result.value().orElseThrow();
    assertEquals(CustomRuleEvaluationStatusV1.PASSED, evaluation.status());
    assertEquals(List.of("draft-version-1"), evaluation.ruleVersionRefs());
    assertEquals(List.of("fact-tenant-1"), evaluation.sourceFactRefs());
    assertEquals(List.of("rule-1"), evaluation.matchedRules());
    assertTrue(evaluation.skippedRules().isEmpty());
    assertTrue(evaluation.blockedRules().isEmpty());
    assertEquals("metadata-adjustment", evaluation.actionOutputs().get(0).actionOutputRef());
    assertEquals("unit-configured", evaluation.actionOutputs().get(0).unit());
    assertEquals("eligibility-service", evaluation.actionOutputs().get(0).sourceService());
    assertEquals(36, evaluation.actionOutputs().get(0).ledgerStepRef().length());
    assertEquals(List.of("reason-configured"), evaluation.reasonCodeRefs());
    assertEquals(64, evaluation.evidenceRef().length());
    assertEquals(evaluation.evidenceRef(), evaluation.replayHash());
    assertTrue(evaluation.replayHashComparison().matches());
    assertEquals(evaluation.evidenceRef(), evaluation.replayHashComparison().evidenceRef());
    assertEquals(evaluation.replayHash(), evaluation.replayHashComparison().replayHash());
    assertEquals(RuleBuilderService.CONSUMER_CONTRACT_VERSION, evaluation.contractVersion());
    CustomRuleEvidenceV1 evidence = evaluation.ruleEvidence().get(0);
    assertEquals(CustomRuleEvidenceStatusV1.MATCHED, evidence.status());
    assertEquals("rule-1", evidence.ruleId());
    assertEquals("draft-version-1", evidence.ruleVersion());
    assertEquals("Metadata driven rule", evidence.ruleName());
    assertEquals("reason-configured", evidence.reasonCodeRef());
    assertEquals(List.of("fact-tenant-1"), evidence.factRefs());
    assertEquals("precision-configured", evidence.precision());
    assertEquals("rounding-configured", evidence.roundingMode());
    assertEquals("unit-configured", evidence.unit());
    assertEquals("eligibility-service", evidence.sourceService());
    assertEquals(evaluation.replayHash(), evidence.replayHash());
    assertEquals(RuleBuilderFactQuality.CONFIRMED, evidence.sourceFacts().get(0).quality());
    assertEquals(RuleBuilderService.EVALUATION_COMPLETED_EVENT_TYPE, service.outboxEvents().get(0).eventType());
    assertEquals(TENANT_ONE + ":request-PII-20-S02", service.outboxEvents().get(0).causationId());
  }

  @Test
  void publishedRuleVersionContractEmitsSkippedEvidenceWithoutCalculationOutputs() {
    CustomRuleEvaluationResultV1 evaluation =
        service
            .evaluatePublishedRuleVersion(
                consumerRequest(
                    new CustomRuleFactV1("dimension-configured", "OTHER_CONFIGURED_VALUE", "fact-tenant-1", RuleBuilderFactQuality.CONFIRMED, "scenario-service", "precision-configured"),
                    "draft-version-1",
                    metadata(),
                    dynamicRuleSet()))
            .value()
            .orElseThrow();

    assertEquals(CustomRuleEvaluationStatusV1.PASSED, evaluation.status());
    assertTrue(evaluation.matchedRules().isEmpty());
    assertEquals(List.of("rule-1"), evaluation.skippedRules());
    assertTrue(evaluation.blockedRules().isEmpty());
    assertTrue(evaluation.actionOutputs().isEmpty());
    CustomRuleEvidenceV1 evidence = evaluation.ruleEvidence().get(0);
    assertEquals(CustomRuleEvidenceStatusV1.SKIPPED, evidence.status());
    assertEquals("RULE_CONDITIONS_NOT_MATCHED", evidence.reasonCodeRef());
    assertEquals(List.of("fact-tenant-1"), evidence.factRefs());
    assertTrue(evidence.actionOutputs().isEmpty());
    assertEquals("", evidence.unit());
    assertEquals("eligibility-service", evidence.sourceService());
    assertTrue(evaluation.replayHashComparison().matches());
  }

  @Test
  void publishedRuleVersionContractFailsClosedForUnknownAndConflictingFacts() {
    CustomRuleEvaluationResultV1 absent =
        service
            .evaluatePublishedRuleVersion(consumerRequest(List.of(), "draft-version-1", metadata(), dynamicRuleSet()))
            .value()
            .orElseThrow();
    assertEquals(CustomRuleEvaluationStatusV1.REQUIRED_FACT_UNKNOWN, absent.status());
    assertTrue(absent.sourceFactRefs().isEmpty());
    assertTrue(absent.actionOutputs().isEmpty());
    assertEquals(List.of("rule-1"), absent.blockedRules());
    CustomRuleEvidenceV1 absentEvidence = absent.ruleEvidence().get(0);
    assertEquals(CustomRuleEvidenceStatusV1.BLOCKED, absentEvidence.status());
    assertEquals("UNKNOWN_FACT_FAIL_CLOSED", absentEvidence.reasonCodeRef());
    assertTrue(absentEvidence.sourceFacts().isEmpty());
    assertEquals(1, absentEvidence.missingFacts().size());
    CustomRuleMissingFactEvidenceV1 missingFact = absentEvidence.missingFacts().get(0);
    assertEquals("dimension-configured", missingFact.dimensionRef());
    assertEquals(RuleBuilderFactQuality.UNKNOWN, missingFact.quality());
    assertEquals("value-source-catalog", missingFact.missingSourceRef());
    assertEquals("eligibility-service", missingFact.recoveryOwnerService());

    CustomRuleEvaluationResultV1 unknown =
        service
            .evaluatePublishedRuleVersion(
                consumerRequest(
                    new CustomRuleFactV1("dimension-configured", "CONFIGURED_VALUE", "fact-tenant-1", RuleBuilderFactQuality.UNKNOWN, "scenario-service", "precision-configured"),
                    "draft-version-1",
                    metadata(),
                    dynamicRuleSet()))
            .value()
            .orElseThrow();
    assertEquals(CustomRuleEvaluationStatusV1.REQUIRED_FACT_UNKNOWN, unknown.status());
    assertTrue(unknown.actionOutputs().isEmpty());
    assertEquals(List.of("rule-1"), unknown.blockedRules());
    CustomRuleEvidenceV1 unknownEvidence = unknown.ruleEvidence().get(0);
    assertEquals(CustomRuleEvidenceStatusV1.BLOCKED, unknownEvidence.status());
    assertEquals("UNKNOWN_FACT_FAIL_CLOSED", unknownEvidence.reasonCodeRef());
    assertTrue(unknownEvidence.actionOutputs().isEmpty());
    assertEquals(RuleBuilderFactQuality.UNKNOWN, unknownEvidence.sourceFacts().get(0).quality());
    assertTrue(service.outboxEvents().isEmpty());

    CustomRuleEvaluationResultV1 conflicting =
        service
            .evaluatePublishedRuleVersion(
                consumerRequest(
                    new CustomRuleFactV1("dimension-configured", "CONFIGURED_VALUE", "fact-tenant-1", RuleBuilderFactQuality.CONFLICTING, "scenario-service", "precision-configured"),
                    "draft-version-1",
                    metadata(),
                    dynamicRuleSet()))
            .value()
            .orElseThrow();
    assertEquals(CustomRuleEvaluationStatusV1.REQUIRED_FACT_CONFLICTING, conflicting.status());
    assertTrue(conflicting.actionOutputs().isEmpty());
    CustomRuleEvidenceV1 conflictingEvidence = conflicting.ruleEvidence().get(0);
    assertEquals(CustomRuleEvidenceStatusV1.BLOCKED, conflictingEvidence.status());
    assertEquals("CONFLICTING_FACT_FAIL_CLOSED", conflictingEvidence.reasonCodeRef());
    assertTrue(conflictingEvidence.actionOutputs().isEmpty());
    assertEquals(RuleBuilderFactQuality.CONFLICTING, conflictingEvidence.sourceFacts().get(0).quality());
  }

  @Test
  void publishedRuleVersionContractRefusesMissingOrIncompatibleRuleVersion() {
    CustomRuleEvaluationResultV1 missingVersion =
        service.evaluatePublishedRuleVersion(consumerRequest(confirmedFact(), "missing-version", metadata(), dynamicRuleSet())).value().orElseThrow();
    assertEquals(CustomRuleEvaluationStatusV1.RULE_VERSION_UNAVAILABLE, missingVersion.status());
    assertTrue(missingVersion.actionOutputs().isEmpty());

    RuleBuilderRuleSet incompatibleRuleSet =
        new RuleBuilderRuleSet(
            dynamicRuleSet().ruleSetId(),
            dynamicRuleSet().schemaVersion(),
            dynamicRuleSet().name(),
            dynamicRuleSet().context(),
            dynamicRuleSet().precedenceStrategy(),
            dynamicRuleSet().rules(),
            List.of("metadata-other-version"),
            dynamicRuleSet().reasonCodeRefs());
    CustomRuleEvaluationResultV1 incompatible =
        service.evaluatePublishedRuleVersion(consumerRequest(confirmedFact(), "draft-version-1", metadata(), incompatibleRuleSet)).value().orElseThrow();
    assertEquals(CustomRuleEvaluationStatusV1.RULE_VERSION_UNAVAILABLE, incompatible.status());
    assertTrue(incompatible.actionOutputs().isEmpty());
  }

  @Test
  void goldenFixtureFamiliesCoverHappyWarningBlockedAndReplayHashCases() throws Exception {
    String fixtureManifest =
        Files.readString(Path.of("golden/PII-20-custom-rules-backend/custom-rule-fixtures-negative-cases.json"));
    for (String category :
        List.of(
            "confirmed-happy-path",
            "estimated-warning-path",
            "unknown-required-fact-blocked",
            "conflicting-required-fact-blocked",
            "missing-rule-version",
            "duplicate-priority",
            "unsupported-operator",
            "unit-conversion-not-declared",
            "replay-hash-stability")) {
      assertTrue(fixtureManifest.contains(category));
    }
    for (String expectedField : List.of("status", "matchedRules", "skippedRules", "blockedRules", "reasonCodeRefs", "evidenceRef", "replayHash")) {
      assertTrue(fixtureManifest.contains(expectedField));
    }

    CustomRuleEvaluationResultV1 firstConfirmed =
        service.evaluatePublishedRuleVersion(consumerRequest(confirmedFact(), "draft-version-1", metadata(), dynamicRuleSet())).value().orElseThrow();
    CustomRuleEvaluationResultV1 replayConfirmed =
        service.evaluatePublishedRuleVersion(consumerRequest(confirmedFact(), "draft-version-1", metadata(), dynamicRuleSet())).value().orElseThrow();
    assertEquals(CustomRuleEvaluationStatusV1.PASSED, firstConfirmed.status());
    assertEquals(firstConfirmed.replayHash(), replayConfirmed.replayHash());
    assertEquals(firstConfirmed.evidenceRef(), replayConfirmed.evidenceRef());

    CustomRuleEvaluationResultV1 estimated =
        service
            .evaluatePublishedRuleVersion(
                consumerRequest(
                    new CustomRuleFactV1("dimension-configured", "CONFIGURED_VALUE", "fact-estimated-1", RuleBuilderFactQuality.ESTIMATED, "scenario-service", "precision-configured"),
                    "draft-version-1",
                    metadata(),
                    dynamicRuleSet()))
            .value()
            .orElseThrow();
    assertEquals(CustomRuleEvaluationStatusV1.PASSED, estimated.status());
    assertEquals(RuleBuilderFactQuality.ESTIMATED, estimated.ruleEvidence().get(0).sourceFacts().get(0).quality());
  }

  @Test
  void goldenNegativeFixturesFailClosedWithoutActionOutputs() {
    CustomRuleEvaluationResultV1 duplicatePriority =
        service.evaluatePublishedRuleVersion(consumerRequest(confirmedFact(), "draft-version-1", metadata(), duplicatePriorityRuleSet())).value().orElseThrow();
    assertEquals(CustomRuleEvaluationStatusV1.POLICY_NOT_SATISFIED, duplicatePriority.status());
    assertEquals(List.of("rule-1", "rule-2"), duplicatePriority.blockedRules());
    assertTrue(duplicatePriority.actionOutputs().isEmpty());
    assertTrue(duplicatePriority.ruleEvidence().stream().anyMatch(evidence -> "DUPLICATE_RULE_PRIORITY".equals(evidence.reasonCodeRef())));

    CustomRuleEvaluationResultV1 unsupportedOperator =
        service
            .evaluatePublishedRuleVersion(
                consumerRequest(confirmedFact(), "draft-version-1", unsupportedOperatorMetadata(), unsupportedOperatorRuleSet()))
            .value()
            .orElseThrow();
    assertEquals(CustomRuleEvaluationStatusV1.POLICY_NOT_SATISFIED, unsupportedOperator.status());
    assertEquals(List.of("rule-1"), unsupportedOperator.blockedRules());
    assertTrue(unsupportedOperator.actionOutputs().isEmpty());
    assertEquals("OPERATOR_EVALUATION_UNSUPPORTED", unsupportedOperator.ruleEvidence().get(0).reasonCodeRef());

    CustomRuleEvaluationResultV1 undeclaredUnit =
        service
            .evaluatePublishedRuleVersion(
                consumerRequest(confirmedFact(), "draft-version-1", metadataWithoutActionUnit(), dynamicRuleSet()))
            .value()
            .orElseThrow();
    assertEquals(CustomRuleEvaluationStatusV1.POLICY_NOT_SATISFIED, undeclaredUnit.status());
    assertEquals(List.of("rule-1"), undeclaredUnit.blockedRules());
    assertTrue(undeclaredUnit.actionOutputs().isEmpty());
    assertEquals("UNIT_CONVERSION_NOT_DECLARED", undeclaredUnit.ruleEvidence().get(0).reasonCodeRef());
  }

  private RuleBuilderDraftCommand draftCommand(String idempotencyKey, RuleBuilderRuleSet ruleSet) {
    return draftCommand(idempotencyKey, metadata(), ruleSet);
  }

  private RuleBuilderDraftCommand draftCommand(String idempotencyKey, RuleBuilderMetadata metadata, RuleBuilderRuleSet ruleSet) {
    return new RuleBuilderDraftCommand(
        TENANT_ONE,
        idempotencyKey,
        "admin-editor-1",
        List.of(RuleBuilderService.WRITE_PERMISSION),
        metadata,
        ruleSet,
        "corr-PII-12-S05");
  }

  private RuleBuilderDynamicEvaluationCommand dynamicCommand(Map<String, RuleBuilderTypedFact> facts) {
    return new RuleBuilderDynamicEvaluationCommand(
        TENANT_ONE,
        "admin-editor-1",
        List.of(RuleBuilderService.SIMULATE_PERMISSION),
        metadata(),
        dynamicRuleSet(),
        facts,
        "draft-version-1",
        "corr-PII-20-S01");
  }

  private CustomRuleEvaluationRequestV1 consumerRequest(
      CustomRuleFactV1 fact, String requestedRuleVersionRef, RuleBuilderMetadata metadata, RuleBuilderRuleSet ruleSet) {
    return consumerRequest(List.of(fact), requestedRuleVersionRef, metadata, ruleSet);
  }

  private CustomRuleEvaluationRequestV1 consumerRequest(
      List<CustomRuleFactV1> facts, String requestedRuleVersionRef, RuleBuilderMetadata metadata, RuleBuilderRuleSet ruleSet) {
    return new CustomRuleEvaluationRequestV1(
        TENANT_ONE,
        "request-PII-20-S02",
        "eligibility-service",
        LocalDate.parse("2026-06-04"),
        new CustomRuleScenarioRefV1("scenario-1", "scenario-version-1"),
        facts,
        List.of("draft-version-1"),
        requestedRuleVersionRef,
        Map.of("consumerCapability", "eligibility-decision"),
        "corr-PII-20-S02",
        "idem-PII-20-S02",
        metadata,
        ruleSet);
  }

  private CustomRuleFactV1 confirmedFact() {
    return new CustomRuleFactV1(
        "dimension-configured", "CONFIGURED_VALUE", "fact-tenant-1", RuleBuilderFactQuality.CONFIRMED, "scenario-service", "precision-configured");
  }

  private RuleBuilderMetadata metadata() {
    return new RuleBuilderMetadata(
        "metadata-rule-builder-2026-06",
        Map.of(
            "dimension-configured",
            new RuleBuilderDimensionMetadata(
                "dimension-configured", "Tenant-configured dimension", List.of("metadata-equals"), List.of("value-source-catalog"))),
        Map.of("metadata-equals", new RuleBuilderOperatorMetadata("metadata-equals", "Equals", "catalog-ref")),
        Map.of("metadata-adjustment", new RuleBuilderActionMetadata("metadata-adjustment", "Tenant-configured action", true, "unit-configured")),
        List.of("PRIORITY_ORDER"),
        List.of("precision-configured"),
        List.of("rounding-configured"),
        List.of("reason-configured"),
        false);
  }

  private RuleBuilderMetadata metadataWithoutActionUnit() {
    return new RuleBuilderMetadata(
        metadata().metadataVersion(),
        metadata().dimensions(),
        metadata().operators(),
        Map.of("metadata-adjustment", new RuleBuilderActionMetadata("metadata-adjustment", "Tenant-configured action", true, "")),
        metadata().precedenceStrategies(),
        metadata().precisionRefs(),
        metadata().roundingRefs(),
        metadata().reasonCodeRefs(),
        false);
  }

  private RuleBuilderMetadata unsupportedOperatorMetadata() {
    return new RuleBuilderMetadata(
        metadata().metadataVersion(),
        Map.of(
            "dimension-configured",
            new RuleBuilderDimensionMetadata(
                "dimension-configured", "Tenant-configured dimension", List.of("metadata-greater-than"), List.of("value-source-catalog"))),
        Map.of("metadata-greater-than", new RuleBuilderOperatorMetadata("metadata-greater-than", "Greater than", "catalog-ref")),
        metadata().actions(),
        metadata().precedenceStrategies(),
        metadata().precisionRefs(),
        metadata().roundingRefs(),
        metadata().reasonCodeRefs(),
        false);
  }

  private RuleBuilderMetadata mortgagePricingMetadata() {
    return new RuleBuilderMetadata(
        "metadata-mortgage-pricing-2026-06",
        Map.of(
            "product.requirement.code",
            new RuleBuilderDimensionMetadata(
                "product.requirement.code",
                "Mortgage product requirement code",
                List.of("metadata-equals"),
                List.of("value-source-product-catalog"),
                "product",
                "product-catalog-service",
                true,
                "",
                "",
                "effective-date",
                List.of("rule-builder.product-requirement.required"),
                List.of())),
        Map.of("metadata-equals", new RuleBuilderOperatorMetadata("metadata-equals", "Equals", "catalog-ref")),
        Map.of("metadata-adjustment", new RuleBuilderActionMetadata("metadata-adjustment", "Tenant-configured action", true, "unit-configured")),
        List.of("PRIORITY_ORDER"),
        List.of("precision-configured"),
        List.of("rounding-configured"),
        List.of("reason-configured"),
        false);
  }

  private RuleBuilderRuleSet ruleSet() {
    return new RuleBuilderRuleSet(
        "rule-set-tenant-configured",
        "2026.06",
        "Tenant configured rules",
        "pricing-admin",
        "PRIORITY_ORDER",
        List.of(
            new RuleBuilderRule(
                "rule-1",
                "Metadata driven rule",
                true,
                10,
                List.of("group-1"),
                List.of(new RuleBuilderCondition("condition-1", "dimension-configured", "metadata-equals", "value-source-catalog")),
                List.of(new RuleBuilderAction("action-1", "metadata-adjustment", "reason-configured", "precision-configured", "rounding-configured")),
                false)),
        List.of("metadata-rule-builder-2026-06"),
        List.of("reason-configured"));
  }

  private RuleBuilderRuleSet mortgagePricingRuleSet() {
    return new RuleBuilderRuleSet(
        "rule-set-mortgage-pricing-metadata",
        "2026.06",
        "Mortgage pricing metadata rules",
        "pricing-admin",
        "PRIORITY_ORDER",
        List.of(
            new RuleBuilderRule(
                "rule-1",
                "Metadata driven mortgage pricing rule",
                true,
                10,
                List.of("group-1"),
                List.of(new RuleBuilderCondition("condition-1", "product.requirement.code", "metadata-equals", "value-source-product-catalog")),
                List.of(new RuleBuilderAction("action-1", "metadata-adjustment", "reason-configured", "precision-configured", "rounding-configured")),
                false)),
        List.of("metadata-mortgage-pricing-2026-06"),
        List.of("reason-configured"));
  }

  private RuleBuilderRuleSet dynamicRuleSet() {
    return new RuleBuilderRuleSet(
        "rule-set-tenant-configured",
        "2026.06",
        "Tenant configured rules",
        "pricing-admin",
        "PRIORITY_ORDER",
        List.of(
            new RuleBuilderRule(
                "rule-1",
                "Metadata driven rule",
                true,
                10,
                List.of("group-1"),
                List.of(new RuleBuilderCondition("condition-1", "dimension-configured", "metadata-equals", "value-source-catalog", "CONFIGURED_VALUE")),
                List.of(new RuleBuilderAction("action-1", "metadata-adjustment", "reason-configured", "precision-configured", "rounding-configured")),
                false)),
        List.of("metadata-rule-builder-2026-06"),
        List.of("reason-configured"));
  }

  private RuleBuilderRuleSet duplicatePriorityRuleSet() {
    return new RuleBuilderRuleSet(
        dynamicRuleSet().ruleSetId(),
        dynamicRuleSet().schemaVersion(),
        dynamicRuleSet().name(),
        dynamicRuleSet().context(),
        dynamicRuleSet().precedenceStrategy(),
        List.of(
            dynamicRuleSet().rules().get(0),
            new RuleBuilderRule(
                "rule-2",
                "Duplicate priority fixture rule",
                true,
                10,
                List.of("group-1"),
                List.of(new RuleBuilderCondition("condition-2", "dimension-configured", "metadata-equals", "value-source-catalog", "CONFIGURED_VALUE")),
                List.of(new RuleBuilderAction("action-2", "metadata-adjustment", "reason-configured", "precision-configured", "rounding-configured")),
                false)),
        dynamicRuleSet().metadataVersionRefs(),
        dynamicRuleSet().reasonCodeRefs());
  }

  private RuleBuilderRuleSet unsupportedOperatorRuleSet() {
    return new RuleBuilderRuleSet(
        dynamicRuleSet().ruleSetId(),
        dynamicRuleSet().schemaVersion(),
        dynamicRuleSet().name(),
        dynamicRuleSet().context(),
        dynamicRuleSet().precedenceStrategy(),
        List.of(
            new RuleBuilderRule(
                "rule-1",
                "Unsupported operator fixture rule",
                true,
                10,
                List.of("group-1"),
                List.of(new RuleBuilderCondition("condition-1", "dimension-configured", "metadata-greater-than", "value-source-catalog", "CONFIGURED_VALUE")),
                List.of(new RuleBuilderAction("action-1", "metadata-adjustment", "reason-configured", "precision-configured", "rounding-configured")),
                false)),
        dynamicRuleSet().metadataVersionRefs(),
        dynamicRuleSet().reasonCodeRefs());
  }
}
