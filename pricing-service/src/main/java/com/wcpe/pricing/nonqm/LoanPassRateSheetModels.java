package com.wcpe.pricing.nonqm;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public final class LoanPassRateSheetModels {
    private LoanPassRateSheetModels() {}

    public record RateSheetRowRef(
            String rowRef,
            String batchRef,
            String tenantId,
            String productCode,
            String investorCode,
            String channelCode,
            String lockTermRef,
            Map<String, Object> rowPayload,
            BigDecimal rateValue,
            BigDecimal priceValue,
            String marginRef,
            String sourceSystem,
            String sourceProvenance,
            boolean syntheticDevOnly,
            Instant effectiveStart,
            Instant effectiveEnd) {
        public RateSheetRowRef {
            requireText(rowRef, "rowRef");
            requireText(batchRef, "batchRef");
            requireText(tenantId, "tenantId");
            requireText(productCode, "productCode");
            requireSource(sourceSystem, sourceProvenance, syntheticDevOnly);
            validateWindow(effectiveStart, effectiveEnd);
            rowPayload = Map.copyOf(rowPayload == null ? Map.of() : rowPayload);
        }
    }

    public record RateOutputRef(
            String outputRef,
            String tenantId,
            String quoteRef,
            String rowRef,
            String productCode,
            String investorCode,
            String channelCode,
            String lockTermRef,
            Map<String, Object> outputPayload,
            String sourceSystem,
            String sourceProvenance,
            boolean syntheticDevOnly) {
        public RateOutputRef {
            requireText(outputRef, "outputRef");
            requireText(tenantId, "tenantId");
            requireText(productCode, "productCode");
            requireSource(sourceSystem, sourceProvenance, syntheticDevOnly);
            outputPayload = Map.copyOf(outputPayload == null ? Map.of() : outputPayload);
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
