package com.wcpe.quote;

import java.util.UUID;

public record QuoteSelectionResponse(
    UUID selectionId,
    QuoteSelectionStatus status,
    String lockEligibilityRef,
    String auditRef,
    String rejectedReason
) {
    public static QuoteSelectionResponse from(QuoteSelection selection) {
        return new QuoteSelectionResponse(
            selection.selectionId(),
            selection.status(),
            selection.lockEligibilityRef(),
            "audit:" + selection.correlationId() + ":" + selection.selectionId(),
            selection.rejectedReason()
        );
    }
}
