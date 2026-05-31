package com.wcpe.scenario.domain;

import java.util.*;

record MortgagePricingRequest(
    String scenarioId,
    BorrowerInfo borrower,
    LoanInfo loan,
    PropertyInfo property
) {}

record BorrowerInfo(Boolean creditProfilePresent) {}

record LoanInfo(Boolean loanAmountPresent) {}

record PropertyInfo(Boolean propertyValuePresent) {}

record PricingAuditMetadata(
    String ruleSourceStatus,
    String ruleVersionStatus,
    String calculationPath,
    String inputTraceId
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
        } else if (request.borrower().creditProfilePresent() == null) {
            errors.add("borrower.creditProfilePresent: missing required structural presence flag");
        }

        if (request.loan() == null) {
            errors.add("loan: missing required loan object");
        } else if (request.loan().loanAmountPresent() == null) {
            errors.add("loan.loanAmountPresent: missing required structural presence flag");
        }

        if (request.property() == null) {
            errors.add("property: missing required property object");
        } else if (request.property().propertyValuePresent() == null) {
            errors.add("property.propertyValuePresent: missing required structural presence flag");
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

        return String.format("trace-skeleton-structural-%d%d%d",
            hasBorrower ? 1 : 0, hasLoan ? 1 : 0, hasProperty ? 1 : 0);
    }
}
