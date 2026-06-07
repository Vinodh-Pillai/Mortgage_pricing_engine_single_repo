package com.wcpe.mladvisory;

import java.util.List;

public record PricingAdvisoryEvaluation(
    String evaluationId,
    String tenantId,
    String scenarioId,
    String pricingResultId,
    String snapshotId,
    String modelVersionId,
    String status,
    boolean authoritative,
    boolean deterministicPricingUnchanged,
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
    List<AdvisoryReason> reasons) {
  public PricingAdvisoryEvaluation {
    reasons = reasons == null ? List.of() : List.copyOf(reasons);
  }
}
