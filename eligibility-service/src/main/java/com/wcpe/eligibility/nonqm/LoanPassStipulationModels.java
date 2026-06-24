package com.wcpe.eligibility.nonqm;

import java.time.Instant;
import java.util.Map;

public final class LoanPassStipulationModels {
    private LoanPassStipulationModels() {}

    public record StipulationTemplateRef(
            String templateRef,
            String tenantId,
            String productCode,
            String stipulationCode,
            String displayName,
            Map<String, Object> templatePayload,
            String sourceSystem,
            String sourceProvenance,
            String sourcePayloadRef,
            boolean syntheticDevOnly,
            String status,
            Instant effectiveStart,
            Instant effectiveEnd) {
        public StipulationTemplateRef {
            requireText(templateRef, "templateRef");
            requireText(tenantId, "tenantId");
            requireText(stipulationCode, "stipulationCode");
            requireText(displayName, "displayName");
            requireSource(sourceSystem, sourceProvenance, syntheticDevOnly);
            validateWindow(effectiveStart, effectiveEnd);
            templatePayload = Map.copyOf(templatePayload == null ? Map.of() : templatePayload);
            status = status == null || status.isBlank() ? "DRAFT" : status.trim().toUpperCase();
        }
    }

    public record StipulationRuleRef(
            String ruleRef,
            String tenantId,
            String templateRef,
            String productCode,
            String investorCode,
            String channelCode,
            Map<String, Object> rulePayload,
            String reasonCodeRef,
            String sourceSystem,
            String sourceProvenance,
            String sourcePayloadRef,
            boolean syntheticDevOnly,
            String status,
            Instant effectiveStart,
            Instant effectiveEnd) {
        public StipulationRuleRef {
            requireText(ruleRef, "ruleRef");
            requireText(tenantId, "tenantId");
            requireText(templateRef, "templateRef");
            requireSource(sourceSystem, sourceProvenance, syntheticDevOnly);
            validateWindow(effectiveStart, effectiveEnd);
            rulePayload = Map.copyOf(rulePayload == null ? Map.of() : rulePayload);
            status = status == null || status.isBlank() ? "DRAFT" : status.trim().toUpperCase();
        }
    }

    private static void requireSource(String sourceSystem, String sourceProvenance, boolean syntheticDevOnly) {
        requireText(sourceSystem, "sourceSystem");
        requireText(sourceProvenance, "sourceProvenance");
        if ("SYNTHETIC_DEV".equalsIgnoreCase(sourceSystem) && !syntheticDevOnly) {
            throw new IllegalArgumentException("synthetic dev source must be marked syntheticDevOnly");
        }
    }

    private static void validateWindow(Instant effectiveStart, Instant effectiveEnd) {
        if (effectiveStart != null && effectiveEnd != null && !effectiveEnd.isAfter(effectiveStart)) {
            throw new IllegalArgumentException("effectiveEnd must be after effectiveStart");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
