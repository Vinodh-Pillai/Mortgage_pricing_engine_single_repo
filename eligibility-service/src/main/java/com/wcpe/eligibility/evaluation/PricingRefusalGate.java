package com.wcpe.eligibility.evaluation;

import com.wcpe.eligibility.domain.models.*;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Pricing Refusal Gate contract per LLD.
 * Does NOT perform pricing math.
 * REFUSED for non-ELIGIBLE envelopes.
 * ALLOWED_FOR_FUTURE_PRICING for ELIGIBLE envelopes.
 */
@Component
public class PricingRefusalGate {

    public PricingRefusalDecision check(EligibilityEnvelope envelope) {
        if (envelope == null) {
            return new PricingRefusalDecision(
                PricingRefusalDecision.RefusalStatus.REFUSED,
                EligibilityStatus.CANNOT_DECIDE,
                List.of(new ReasonCode("PRICING_MISSING_ENVELOPE", "R00", ReasonSeverity.BLOCKING.name(), "PRICING",
                    "Eligibility envelope is missing.", "Cannot proceed without eligibility envelope.")),
                "Pricing refused: missing eligibility envelope");
        }

        switch (envelope.status()) {
            case ELIGIBLE:
                return new PricingRefusalDecision(
                    PricingRefusalDecision.RefusalStatus.ALLOWED_FOR_FUTURE_PRICING,
                    EligibilityStatus.ELIGIBLE,
                    List.of(),
                    "Pricing allowed for future orchestration (no pricing math in PII-01)");
            case INELIGIBLE:
                return new PricingRefusalDecision(
                    PricingRefusalDecision.RefusalStatus.REFUSED,
                    EligibilityStatus.INELIGIBLE,
                    envelope.reasonCodes(),
                    "Pricing refused: eligibility status is INELIGIBLE");
            case CANNOT_DECIDE:
                return new PricingRefusalDecision(
                    PricingRefusalDecision.RefusalStatus.REFUSED,
                    EligibilityStatus.CANNOT_DECIDE,
                    envelope.reasonCodes(),
                    "Pricing refused: eligibility status is CANNOT_DECIDE");
            case OUT_OF_SCOPE:
                return new PricingRefusalDecision(
                    PricingRefusalDecision.RefusalStatus.REFUSED,
                    EligibilityStatus.OUT_OF_SCOPE,
                    envelope.reasonCodes(),
                    "Pricing refused: eligibility status is OUT_OF_SCOPE");
            default:
                return new PricingRefusalDecision(
                    PricingRefusalDecision.RefusalStatus.REFUSED,
                    EligibilityStatus.CANNOT_DECIDE,
                    List.of(new ReasonCode("PRICING_UNKNOWN_STATUS", "R00", ReasonSeverity.BLOCKING.name(), "PRICING",
                        "Unknown eligibility status.", "Cannot determine pricing eligibility.")),
                    "Pricing refused: unknown eligibility status");
        }
    }

    public record PricingRefusalDecision(
        PricingRefusalDecision.RefusalStatus status,
        EligibilityStatus blockingEligibilityStatus,
        List<ReasonCode> reasonCodes,
        String message
    ) {
        public enum RefusalStatus {
            REFUSED,
            ALLOWED_FOR_FUTURE_PRICING
        }
    }
}
