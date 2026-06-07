package com.wcpe.mladvisory;

import java.time.Instant;

public record FeedbackAggregateQuery(
    String tenantId, String modelVersionId, AdvisoryType advisoryType, Instant from, Instant to) {}
