package com.wcpe.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
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
  }

  private RuleBuilderDraftCommand draftCommand(String idempotencyKey, RuleBuilderRuleSet ruleSet) {
    return new RuleBuilderDraftCommand(
        TENANT_ONE,
        idempotencyKey,
        "admin-editor-1",
        List.of(RuleBuilderService.WRITE_PERMISSION),
        metadata(),
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

  private RuleBuilderMetadata metadata() {
    return new RuleBuilderMetadata(
        "metadata-rule-builder-2026-06",
        Map.of(
            "dimension-configured",
            new RuleBuilderDimensionMetadata(
                "dimension-configured", "Tenant-configured dimension", List.of("metadata-equals"), List.of("value-source-catalog"))),
        Map.of("metadata-equals", new RuleBuilderOperatorMetadata("metadata-equals", "Equals", "catalog-ref")),
        Map.of("metadata-adjustment", new RuleBuilderActionMetadata("metadata-adjustment", "Tenant-configured action", true)),
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
}
