package com.wcpe.quote;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

public record PriceWaterfall(
    BigDecimal basePriceBps,
    BigDecimal totalAdjustmentBps,
    BigDecimal marginBps,
    BigDecimal finalPriceBps,
    Map<String, String> upstreamRefs,
    List<WaterfallSection> sections,
    RoundingTrace roundingTrace
) {
    public PriceWaterfall {
        upstreamRefs = Map.copyOf(upstreamRefs == null ? Map.of() : upstreamRefs);
        sections = List.copyOf(sections == null ? List.of() : sections);
        roundingTrace = roundingTrace == null ? RoundingTrace.empty() : roundingTrace;
        validateLineReasonCodes(sections);
        BigDecimal reconciled = basePriceBps
            .add(totalAdjustmentBps)
            .add(marginBps)
            .setScale(4, RoundingMode.HALF_UP);
        if (reconciled.compareTo(finalPriceBps) != 0) {
            throw new QuoteCreateException("WATERFALL_MISMATCH", "Waterfall lines must reconcile to final price");
        }
    }

    public PriceWaterfall(
        BigDecimal basePriceBps,
        BigDecimal totalAdjustmentBps,
        BigDecimal marginBps,
        BigDecimal finalPriceBps,
        Map<String, String> upstreamRefs
    ) {
        this(basePriceBps, totalAdjustmentBps, marginBps, finalPriceBps, upstreamRefs, List.of());
    }

    public PriceWaterfall(
        BigDecimal basePriceBps,
        BigDecimal totalAdjustmentBps,
        BigDecimal marginBps,
        BigDecimal finalPriceBps,
        Map<String, String> upstreamRefs,
        List<AdjustmentLine> adjustmentLines
    ) {
        this(
            basePriceBps,
            totalAdjustmentBps,
            marginBps,
            finalPriceBps,
            upstreamRefs,
            defaultSections(basePriceBps, totalAdjustmentBps, marginBps, upstreamRefs, adjustmentLines),
            new RoundingTrace("HALF_UP", 4, basePriceBps.add(totalAdjustmentBps).add(marginBps), finalPriceBps)
        );
    }

    public List<WaterfallLine> visibleLinesFor(java.util.Set<String> allowedFields) {
        java.util.Set<String> safeAllowedFields = allowedFields == null ? java.util.Set.of() : allowedFields;
        return sections.stream()
            .flatMap(section -> section.lines().stream())
            .map(line -> line.maskedFor(safeAllowedFields))
            .toList();
    }

    private static List<WaterfallSection> defaultSections(
        BigDecimal basePriceBps,
        BigDecimal totalAdjustmentBps,
        BigDecimal marginBps,
        Map<String, String> upstreamRefs,
        List<AdjustmentLine> adjustmentLines
    ) {
        Map<String, String> refs = upstreamRefs == null ? Map.of() : upstreamRefs;
        String priceRef = refs.getOrDefault("priceRef", "priceRef:unavailable");
        String eligibilityRef = refs.getOrDefault("eligibilityRef", "eligibilityRef:unavailable");
        String marginRef = refs.getOrDefault("marginRef", refs.getOrDefault("priceRef", "marginRef:unavailable"));
        List<WaterfallLine> adjustmentWaterfallLines = adjustmentLines == null || adjustmentLines.isEmpty()
            ? List.of(new WaterfallLine("total-adjustments", "Total adjustments", "TOTAL_ADJUSTMENTS", "adjustment-service", eligibilityRef, "+", totalAdjustmentBps, "VISIBLE", false))
            : adjustmentLines.stream()
                .map(line -> new WaterfallLine(
                    lineId(line),
                    label(line),
                    reasonCode(line),
                    "adjustment-service",
                    ruleRef(line, eligibilityRef),
                    line.amount() < 0 ? "-" : "+",
                    BigDecimal.valueOf(line.amount()).movePointRight(2).setScale(4, RoundingMode.HALF_UP),
                    "VISIBLE",
                    false))
                .toList();
        return List.of(
            new WaterfallSection("base-price", "Base price", 1, List.of(
                new WaterfallLine("base-price", "Base price", "BASE_PRICE", "pricing-service", priceRef, "+", basePriceBps, "VISIBLE", false)
            )),
            new WaterfallSection("eligibility-adjustments", "Eligibility and LLPAs", 2, adjustmentWaterfallLines),
            new WaterfallSection("margin-compensation", "Margins and compensation", 3, List.of(
                new WaterfallLine("margin", "Margin", "MARGIN", "margin-service", marginRef, "+", marginBps, "RESTRICTED", true)
            ))
        );
    }

    private static String lineId(AdjustmentLine line) {
        String key = firstNonBlank(line.ruleId(), line.factorKey(), line.auditRef(), "adjustment");
        return "adjustment-" + key.replaceAll("[^A-Za-z0-9_-]", "-");
    }

    private static String label(AdjustmentLine line) {
        return firstNonBlank(line.label(), line.reason(), line.factorKey(), "Adjustment");
    }

    private static String reasonCode(AdjustmentLine line) {
        return firstNonBlank(line.reason(), line.factorKey(), "ADJUSTMENT_LINE");
    }

    private static String ruleRef(AdjustmentLine line, String fallback) {
        return firstNonBlank(line.sourceRef(), line.auditRef(), line.source(), fallback);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static void validateLineReasonCodes(List<WaterfallSection> sections) {
        for (WaterfallSection section : sections) {
            for (WaterfallLine line : section.lines()) {
                if (line.reasonCode() == null || line.reasonCode().isBlank()) {
                    throw new QuoteCreateException("POLICY_NOT_SATISFIED", "Every waterfall line requires a reason code");
                }
            }
        }
    }

    public record WaterfallSection(String sectionId, String label, int displayOrder, List<WaterfallLine> lines) {
        public WaterfallSection {
            lines = List.copyOf(lines == null ? List.of() : lines);
        }
    }

    public record WaterfallLine(
        String lineId,
        String label,
        String reasonCode,
        String sourceService,
        String ruleVersionRef,
        String sign,
        BigDecimal amountBps,
        String visibility,
        boolean restricted
    ) {
        public WaterfallLine maskedFor(java.util.Set<String> allowedFields) {
            if (!restricted || allowedFields.contains(lineId) || allowedFields.contains("restrictedWaterfall")) {
                return this;
            }
            return new WaterfallLine(lineId, label, reasonCode, sourceService, ruleVersionRef, sign, null, "MASKED", true);
        }
    }

    public record RoundingTrace(String mode, int scale, BigDecimal unroundedFinalPriceBps, BigDecimal roundedFinalPriceBps) {
        private static RoundingTrace empty() {
            return new RoundingTrace("HALF_UP", 4, BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }
}
