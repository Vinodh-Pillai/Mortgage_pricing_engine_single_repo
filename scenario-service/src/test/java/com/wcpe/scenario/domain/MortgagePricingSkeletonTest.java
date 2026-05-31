package com.wcpe.scenario.domain;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MortgagePricingSkeletonTest {

    MortgagePricingSkeleton skeleton = new MortgagePricingSkeleton();

    MortgagePricingRequest validRequest() {
        return new MortgagePricingRequest(
            "SCN-001",
            new BorrowerInfo(true),
            new LoanInfo(true),
            new PropertyInfo(true)
        );
    }

    @Test
    void structurallyValidRequestReturnsBlockedUnavailableRules() {
        MortgagePricingResponse response = skeleton.evaluateMortgagePricingSkeleton(validRequest());

        assertThat(response.scenarioId()).isEqualTo("SCN-001");
        assertThat(response.pricingResult()).isNull();
        assertThat(response.calculationStatus()).isEqualTo("blocked_unavailable_rules");
        assertThat(response.validationErrors()).isEmpty();
        assertThat(response.auditMetadata().ruleSourceStatus()).isEqualTo("unresolved");
        assertThat(response.auditMetadata().ruleVersionStatus()).isEqualTo("unavailable");
        assertThat(response.auditMetadata().calculationPath()).isEqualTo("not_started_rules_unavailable");
        assertThat(response.auditMetadata().inputTraceId()).isEqualTo("trace-skeleton-structural-111");
    }

    @Test
    void structurallyValidRequestTraceIdIsDeterministic() {
        MortgagePricingResponse r1 = skeleton.evaluateMortgagePricingSkeleton(validRequest());
        MortgagePricingResponse r2 = skeleton.evaluateMortgagePricingSkeleton(validRequest());

        assertThat(r2.auditMetadata().inputTraceId()).isEqualTo(r1.auditMetadata().inputTraceId());
    }

    @Test
    void missingScenarioIdReturnsValidationFailed() {
        MortgagePricingRequest request = new MortgagePricingRequest(
            null,
            new BorrowerInfo(true),
            new LoanInfo(true),
            new PropertyInfo(true)
        );

        MortgagePricingResponse response = skeleton.evaluateMortgagePricingSkeleton(request);

        assertThat(response.pricingResult()).isNull();
        assertThat(response.calculationStatus()).isEqualTo("validation_failed");
        assertThat(response.validationErrors()).anyMatch(e -> e.contains("scenarioId"));
        assertThat(response.auditMetadata().ruleSourceStatus()).isEqualTo("unresolved");
        assertThat(response.auditMetadata().ruleVersionStatus()).isEqualTo("unavailable");
        assertThat(response.auditMetadata().calculationPath()).isEqualTo("not_started_validation_failed");
    }

    @Test
    void missingBorrowerReturnsValidationFailed() {
        MortgagePricingRequest request = new MortgagePricingRequest(
            "SCN-002",
            null,
            new LoanInfo(true),
            new PropertyInfo(true)
        );

        MortgagePricingResponse response = skeleton.evaluateMortgagePricingSkeleton(request);

        assertThat(response.scenarioId()).isEqualTo("SCN-002");
        assertThat(response.pricingResult()).isNull();
        assertThat(response.calculationStatus()).isEqualTo("validation_failed");
        assertThat(response.validationErrors()).anyMatch(e -> e.contains("borrower"));
    }

    @Test
    void missingStructuralPresenceFlagsReturnValidationFailed() {
        MortgagePricingRequest request = new MortgagePricingRequest(
            "SCN-005",
            new BorrowerInfo(null),
            new LoanInfo(null),
            new PropertyInfo(null)
        );

        MortgagePricingResponse response = skeleton.evaluateMortgagePricingSkeleton(request);

        assertThat(response.pricingResult()).isNull();
        assertThat(response.calculationStatus()).isEqualTo("validation_failed");
        assertThat(response.validationErrors())
            .containsExactly(
                "borrower.creditProfilePresent: missing required structural presence flag",
                "loan.loanAmountPresent: missing required structural presence flag",
                "property.propertyValuePresent: missing required structural presence flag"
            );
        assertThat(response.auditMetadata().calculationPath()).isEqualTo("not_started_validation_failed");
    }

    @Test
    void missingLoanReturnsValidationFailed() {
        MortgagePricingRequest request = new MortgagePricingRequest(
            "SCN-003",
            new BorrowerInfo(true),
            null,
            new PropertyInfo(true)
        );

        MortgagePricingResponse response = skeleton.evaluateMortgagePricingSkeleton(request);

        assertThat(response.validationErrors()).anyMatch(e -> e.contains("loan"));
    }

    @Test
    void missingPropertyReturnsValidationFailed() {
        MortgagePricingRequest request = new MortgagePricingRequest(
            "SCN-004",
            new BorrowerInfo(true),
            new LoanInfo(true),
            null
        );

        MortgagePricingResponse response = skeleton.evaluateMortgagePricingSkeleton(request);

        assertThat(response.validationErrors()).anyMatch(e -> e.contains("property"));
    }

    @Test
    void nullRequestReturnsValidationFailedWithNullScenarioId() {
        MortgagePricingResponse response = skeleton.evaluateMortgagePricingSkeleton(null);

        assertThat(response.scenarioId()).isNull();
        assertThat(response.pricingResult()).isNull();
        assertThat(response.calculationStatus()).isEqualTo("validation_failed");
        assertThat(response.validationErrors()).isNotEmpty();
        assertThat(response.auditMetadata().ruleSourceStatus()).isEqualTo("unresolved");
        assertThat(response.auditMetadata().ruleVersionStatus()).isEqualTo("unavailable");
        assertThat(response.auditMetadata().calculationPath()).isEqualTo("not_started_validation_failed");
    }

    @Test
    void multipleMissingFieldsReturnMultipleValidationErrors() {
        MortgagePricingRequest request = new MortgagePricingRequest(
            "",
            null,
            null,
            null
        );

        MortgagePricingResponse response = skeleton.evaluateMortgagePricingSkeleton(request);

        assertThat(response.calculationStatus()).isEqualTo("validation_failed");
        assertThat(response.validationErrors()).hasSize(4);
    }
}
