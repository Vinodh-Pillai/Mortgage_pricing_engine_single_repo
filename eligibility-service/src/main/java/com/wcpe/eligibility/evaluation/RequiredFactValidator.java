package com.wcpe.eligibility.evaluation;

import com.wcpe.eligibility.domain.models.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates that all required facts are present before rule evaluation.
 * Returns CANNOT_DECIDE with blocking reason codes for missing/unknown/conflicting facts.
 */
@Component
public class RequiredFactValidator {

    public List<MissingFact> validate(EligibilityRequest request) {
        List<MissingFact> missing = new ArrayList<>();

        BorrowerProfile borrower = request.borrowerProfile();
        PropertyProfile property = request.propertyProfile();
        LoanProfile loan = request.loanProfile();
        ProductCandidate candidate = request.productCandidate();

        // Borrower facts
        if (borrower == null) {
            missing.add(new MissingFact("borrowerProfile", "Borrower profile is required."));
        } else {
            if (borrower.representativeFico() == null) {
                missing.add(new MissingFact("representativeFico", "FICO score is required."));
            }
        }

        // Property facts
        if (property == null) {
            missing.add(new MissingFact("propertyProfile", "Property profile is required."));
        } else {
            if (property.propertyType() == null || property.propertyType().isBlank()) {
                missing.add(new MissingFact("propertyType", "Property type is required."));
            }
            if (property.occupancyType() == null || property.occupancyType().isBlank()) {
                missing.add(new MissingFact("occupancyType", "Occupancy type is required."));
            }
            if (property.state() == null || property.state().isBlank()) {
                missing.add(new MissingFact("state", "State is required."));
            }
            if (property.purchasePrice() == null) {
                missing.add(new MissingFact("purchasePrice", "Purchase price is required."));
            }
        }

        // Loan facts
        if (loan == null) {
            missing.add(new MissingFact("loanProfile", "Loan profile is required."));
        } else {
            if (loan.loanPurpose() == null || loan.loanPurpose().isBlank()) {
                missing.add(new MissingFact("loanPurpose", "Loan purpose is required."));
            }
            if (loan.loanAmount() == null) {
                missing.add(new MissingFact("loanAmount", "Loan amount is required."));
            }
            if (loan.documentationType() == null || loan.documentationType().isBlank()) {
                missing.add(new MissingFact("documentationType", "Documentation type is required."));
            }
        }

        // Candidate facts
        if (candidate == null) {
            missing.add(new MissingFact("productCandidate", "Product candidate is required."));
        }

        return missing;
    }

    public record MissingFact(
        String field,
        String message
    ) {}
}
