package com.wcpe.quote;

import java.util.List;

public record QuoteSelectionPolicy(
    String policyId,
    String policyVersion,
    boolean permissionGranted,
    boolean allowNonTopRank,
    boolean allowReselection,
    List<String> requiredAcknowledgements
) {
    public QuoteSelectionPolicy {
        requiredAcknowledgements = List.copyOf(requiredAcknowledgements == null ? List.of() : requiredAcknowledgements);
    }
}
