package com.wcpe.quote;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record QuoteExplanationResponse(
    UUID quoteId,
    UUID optionId,
    List<PriceWaterfall.WaterfallSection> waterfallSections,
    BigDecimal finalPrice,
    PriceWaterfall.RoundingTrace roundingTrace,
    Map<String, String> upstreamRefs,
    List<String> hiddenFields,
    List<String> complianceFlags,
    String auditRef
) {
    public QuoteExplanationResponse {
        waterfallSections = List.copyOf(waterfallSections == null ? List.of() : waterfallSections);
        upstreamRefs = Map.copyOf(upstreamRefs == null ? Map.of() : upstreamRefs);
        hiddenFields = List.copyOf(hiddenFields == null ? List.of() : hiddenFields);
        complianceFlags = List.copyOf(complianceFlags == null ? List.of() : complianceFlags);
    }
}
