package com.wcpe.quote;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record QuoteSelection(
    UUID tenantId,
    UUID selectionId,
    UUID quoteId,
    UUID optionId,
    QuoteSelectionStatus status,
    String selectionPolicyId,
    String selectionPolicyVersion,
    String selectedBy,
    Instant selectedAt,
    List<String> acknowledgements,
    String lockEligibilityRef,
    String rejectedReason,
    String idempotencyKey,
    String correlationId,
    Map<String, String> lineageRefs,
    int version
) {
    public QuoteSelection {
        acknowledgements = List.copyOf(acknowledgements == null ? List.of() : acknowledgements);
        lineageRefs = Map.copyOf(lineageRefs == null ? Map.of() : lineageRefs);
    }
}
