package com.wcpe.scenario.domain;

import java.util.*;
import java.util.stream.*;

record MortgagePricingRequest(
    String scenarioId,
    BorrowerInfo borrower,
    LoanInfo loan,
    PropertyInfo property
) {}

record BorrowerInfo(boolean creditProfilePresent) {}

record LoanInfo(boolean loanAmountPresent) {}

record PropertyInfo(boolean propertyValuePresent) {}

record PricingAuditMetadata(
    String ruleSourceStatus,
    String ruleVersionStatus,
    String calculationPath,
    String inputTraceId
) {}

record PricingResult(
    String calculationStatus,
    Object pricingResult
) {}

record MortgagePricingResponse(
    String scenarioId,
    Object pricingResult,
    String calculationStatus,
    List<String> validationErrors,
    PricingAuditMetadata auditMetadata
) {}

/**
 * PII-01 walking-skeleton pricing component.
 * Structurally valid requests return blocked_unavailable_rules.
 * Structurally invalid requests return validation_failed with field errors.
 * No business-correct pricing logic is implemented; that requires approved rule sources.
 */
class MortgagePricingSkeleton {

    public MortgagePricingResponse evaluateMortgagePricingSkeleton(MortgagePricingRequest request) {
        List<String> errors = validate(request);

        if (!errors.isEmpty()) {
            return new MortgagePricingResponse(
                request != null ? request.scenarioId() : null,
                null,
                "validation_failed",
                errors,
                new PricingAuditMetadata(
                    "unresolved",
                    "unavailable",
                    "not_started_validation_failed",
                    generateTraceId(request)
                )
            );
        }

        return new MortgagePricingResponse(
            request.scenarioId(),
            null,
            "blocked_unavailable_rules",
            List.of(),
            new PricingAuditMetadata(
                "unresolved",
                "unavailable",
                "not_started_rules_unavailable",
                generateTraceId(request)
            )
        );
    }

    List<String> validate(MortgagePricingRequest request) {
        List<String> errors = new ArrayList<>();

        if (request == null) {
            errors.add("request: missing required request object");
            return errors;
        }

        if (request.scenarioId() == null || request.scenarioId().isBlank()) {
            errors.add("scenarioId: missing required scenario identifier");
        }

        if (request.borrower() == null) {
            errors.add("borrower: missing required borrower object");
        }

        if (request.loan() == null) {
            errors.add("loan: missing required loan object");
        }

        if (request.property() == null) {
            errors.add("property: missing required property object");
        }

        return errors;
    }

    String generateTraceId(MortgagePricingRequest request) {
        if (request == null) {
            return "trace-skeleton-null";
        }

        boolean hasBorrower = request.borrower() != null;
        boolean hasLoan = request.loan() != null;
        boolean hasProperty = request.property() != null;

        String sid = request.scenarioId() != null ? request.scenarioId().substring(0, Math.min(8, request.scenarioId().length())) : "nosid";

        return String.format("trace-skeleton-%s-%d%d%d", sid,
            hasBorrower ? 1 : 0, hasLoan ? 1 : 0, hasProperty ? 1 : 0);
    }
}
