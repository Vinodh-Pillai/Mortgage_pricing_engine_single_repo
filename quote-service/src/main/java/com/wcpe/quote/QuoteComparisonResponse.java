package com.wcpe.quote;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record QuoteComparisonResponse(
    UUID quoteId,
    String status,
    String viewConfigVersion,
    List<ComparisonColumn> columns,
    List<Map<String, Object>> rows,
    List<ComparisonHiddenField> hiddenFields,
    List<String> warnings,
    String auditRef
) {
}
