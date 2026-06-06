package com.wcpe.eligibility.domain.models;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record QuoteSubmissionResponse(
    UUID quoteId,
    UUID scenarioId,
    int scenarioVersion,
    String quoteStatus,
    int eligibleOptionCount,
    int ineligibleOptionCount,
    int warningOptionCount,
    List<QuoteOptionOutput> options,
    UUID auditPackageId,
    String resultHash,
    String correlationId,
    Instant createdAt
) {
    public record QuoteOptionOutput(
        UUID quoteOptionId,
        String productCode,
        String investorCode,
        String eligibilityStatus,
        String pricingStatus,
        String summaryReason
    ) {}
}
