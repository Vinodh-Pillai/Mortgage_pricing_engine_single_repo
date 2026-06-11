package com.wcpe.adjustment;

import java.util.List;
import java.util.Objects;

/**
 * Policy-neutral adjustment evidence view for PII-22-S17.
 *
 * <p>The module carries ids, fact refs, sources, conflict metadata, compensation hook refs,
 * and summaries supplied by configured adjustment components. It does not calculate or infer LLPA,
 * fee, compensation, investor, or compliance values.</p>
 */
public final class AdjustmentEvidenceModule {
    public AdjustmentEvidenceView view(AdjustmentEvidenceRequest request) {
        Objects.requireNonNull(request, "adjustment evidence request is required");
        request.validate();
        return new AdjustmentEvidenceView(
            request.tenantContext(),
            request.dependencyStatus(),
            request.configured() && request.blockers().isEmpty() ? "READY" : "BLOCKED",
            request.adjustments(),
            request.conflicts(),
            request.blockers(),
            request.summaries(),
            request.versionRefs(),
            request.auditRefs(),
            request.replayHash(),
            request.correlationId()
        );
    }

    public record AdjustmentEvidenceRequest(
        String tenantContext,
        boolean configured,
        String dependencyStatus,
        List<AdjustmentEvidenceLine> adjustments,
        List<AdjustmentConflictEvidence> conflicts,
        List<AdjustmentBlockedState> blockers,
        List<AdjustmentSummaryEvidence> summaries,
        List<String> versionRefs,
        List<String> auditRefs,
        String replayHash,
        String correlationId
    ) {
        public AdjustmentEvidenceRequest {
            tenantContext = requireText(tenantContext, "tenantContext is required");
            dependencyStatus = requireText(dependencyStatus, "dependencyStatus is required");
            adjustments = List.copyOf(adjustments == null ? List.of() : adjustments);
            conflicts = List.copyOf(conflicts == null ? List.of() : conflicts);
            blockers = List.copyOf(blockers == null ? List.of() : blockers);
            summaries = List.copyOf(summaries == null ? List.of() : summaries);
            versionRefs = copyTextRefs(versionRefs, "versionRef");
            auditRefs = copyTextRefs(auditRefs, "auditRef");
            replayHash = requireText(replayHash, "replayHash is required");
            correlationId = requireText(correlationId, "correlationId is required");
        }

        void validate() {
            if (adjustments.isEmpty()) {
                throw new IllegalArgumentException("at least one adjustment evidence line is required");
            }
            if (!configured && blockers.isEmpty()) {
                throw new IllegalArgumentException("missing configuration requires at least one blocked state");
            }
        }
    }

    public record AdjustmentEvidenceLine(
        String adjustmentId,
        String label,
        String category,
        String status,
        List<String> factRefs,
        String sourceRef,
        String sourceVersionRef,
        String summary,
        List<String> compensationHookRefs,
        List<String> conflictIds
    ) {
        public AdjustmentEvidenceLine {
            adjustmentId = requireText(adjustmentId, "adjustmentId is required");
            label = requireText(label, "label is required");
            category = requireText(category, "category is required");
            status = requireText(status, "status is required");
            factRefs = copyTextRefs(factRefs, "factRef");
            sourceRef = requireText(sourceRef, "sourceRef is required");
            sourceVersionRef = requireText(sourceVersionRef, "sourceVersionRef is required");
            summary = requireText(summary, "summary is required");
            compensationHookRefs = copyTextRefs(compensationHookRefs, "compensationHookRef");
            conflictIds = copyTextRefs(conflictIds, "conflictId");
        }
    }

    public record AdjustmentConflictEvidence(
        String conflictId,
        String severity,
        String reasonCode,
        String reason,
        String resolutionOwner,
        List<String> affectedAdjustmentIds
    ) {
        public AdjustmentConflictEvidence {
            conflictId = requireText(conflictId, "conflictId is required");
            severity = requireText(severity, "severity is required");
            reasonCode = requireText(reasonCode, "reasonCode is required");
            reason = requireText(reason, "reason is required");
            resolutionOwner = requireText(resolutionOwner, "resolutionOwner is required");
            affectedAdjustmentIds = copyTextRefs(affectedAdjustmentIds, "affectedAdjustmentId");
        }
    }

    public record AdjustmentBlockedState(String reasonCode, String message, String sourceRef, String resolutionOwner) {
        public AdjustmentBlockedState {
            reasonCode = requireText(reasonCode, "reasonCode is required");
            message = requireText(message, "message is required");
            sourceRef = requireText(sourceRef, "sourceRef is required");
            resolutionOwner = requireText(resolutionOwner, "resolutionOwner is required");
        }
    }

    public record AdjustmentSummaryEvidence(String category, String summary, List<String> evidenceRefs) {
        public AdjustmentSummaryEvidence {
            category = requireText(category, "category is required");
            summary = requireText(summary, "summary is required");
            evidenceRefs = copyTextRefs(evidenceRefs, "evidenceRef");
        }
    }

    public record AdjustmentEvidenceView(
        String tenantContext,
        String dependencyStatus,
        String status,
        List<AdjustmentEvidenceLine> adjustments,
        List<AdjustmentConflictEvidence> conflicts,
        List<AdjustmentBlockedState> blockers,
        List<AdjustmentSummaryEvidence> summaries,
        List<String> versionRefs,
        List<String> auditRefs,
        String replayHash,
        String correlationId
    ) {}

    static List<String> copyTextRefs(List<String> values, String label) {
        return List.copyOf((values == null ? List.<String>of() : values).stream()
            .map(value -> requireText(value, label + " is required"))
            .toList());
    }

    static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
