package com.wcpe.eligibility.domain.models;

import java.util.List;
import java.util.Map;

/**
 * Per-candidate eligibility envelope per LLD API contract.
 */
public record EligibilityEnvelope(
    String tenantId,
    String scenarioId,
    String scenarioSnapshotVersion,
    String productOptionId,
    String investor,
    String channel,
    String productCatalogVersion,
    String eligibilityRuleSetVersion,
    String fixtureVersion,
    String evaluationTimestamp,
    EligibilityStatus status,
    List<ReasonCode> reasonCodes,
    List<RuleActualValue> actualValues,
    String replayHash
) {}
