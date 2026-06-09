package com.wcpe.quote;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record QuoteSelectionResponse(
    UUID selectionId,
    UUID optionId,
    QuoteSelectionStatus status,
    int scenarioVersion,
    String lockEligibilityRef,
    String snapshotRef,
    String auditRef,
    List<String> auditIds,
    Map<String, String> lineageRefs,
    String rejectedReason
) {
    public static QuoteSelectionResponse from(QuoteSelection selection) {
        String scenarioVersion = selection.lineageRefs().getOrDefault("scenarioVersion", "0");
        int parsedScenarioVersion = scenarioVersion.matches("\\d+") ? Integer.parseInt(scenarioVersion) : 0;
        String auditRef = "audit:" + selection.correlationId() + ":" + selection.selectionId();
        return new QuoteSelectionResponse(
            selection.selectionId(),
            selection.optionId(),
            selection.status(),
            parsedScenarioVersion,
            selection.lockEligibilityRef(),
            selection.lineageRefs().getOrDefault("snapshotRef", ""),
            auditRef,
            List.of(auditRef, selection.lineageRefs().getOrDefault("auditRef", ""), selection.lineageRefs().getOrDefault("quoteReplayHash", "")),
            selection.lineageRefs(),
            selection.rejectedReason()
        );
    }
}
