package com.wcpe.mladvisory;

import java.time.Instant;

public record FeedbackAggregate(
    String tenantId,
    String modelVersionId,
    AdvisoryType advisoryType,
    String confidenceBand,
    Instant periodStart,
    Instant periodEnd,
    int usefulCount,
    int notUsefulCount,
    int concernCount) {
  FeedbackAggregate increment(FeedbackOutcome outcome) {
    int useful = usefulCount + (outcome == FeedbackOutcome.USEFUL ? 1 : 0);
    int notUseful = notUsefulCount + (outcome == FeedbackOutcome.NOT_USEFUL ? 1 : 0);
    int concerns = concernCount + (outcome == FeedbackOutcome.REPORT_CONCERN ? 1 : 0);
    return new FeedbackAggregate(
        tenantId, modelVersionId, advisoryType, confidenceBand, periodStart, periodEnd, useful, notUseful, concerns);
  }
}
