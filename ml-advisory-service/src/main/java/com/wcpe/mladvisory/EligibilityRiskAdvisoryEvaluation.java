package com.wcpe.mladvisory;

import java.util.List;

public record EligibilityRiskAdvisoryEvaluation(
    String evaluationId,
    String tenantId,
    String scenarioId,
    String eligibilityResultId,
    String eligibilityResultVersion,
    String snapshotId,
    String modelVersionId,
    String status,
    boolean authoritative,
    boolean notAdverseAction,
    boolean eligibilityUnchanged,
    String riskBand,
    double confidence,
    String confidenceBand,
    String suppressionReason,
    String disclaimer,
    String advisoryId,
    String explanationUrl,
    String feedbackUrl,
    String eventRef,
    String auditRef,
    String correlationId,
    List<AdvisoryReason> reviewPrompts) {
  public EligibilityRiskAdvisoryEvaluation {
    reviewPrompts = reviewPrompts == null ? List.of() : List.copyOf(reviewPrompts);
  }
}
