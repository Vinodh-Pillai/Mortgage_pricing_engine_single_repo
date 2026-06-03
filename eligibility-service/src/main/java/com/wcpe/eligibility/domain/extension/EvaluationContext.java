package com.wcpe.eligibility.domain.extension;

import com.wcpe.eligibility.domain.models.EligibilityRequest;

import java.time.LocalDate;
import java.util.UUID;

public record EvaluationContext(
    UUID tenantId,
    LocalDate asOfDate,
    EligibilityRequest request
) {}
