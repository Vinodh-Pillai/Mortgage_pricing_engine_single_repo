package com.wcpe.quote;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

public record ComparisonViewConfig(
    String viewId,
    String version,
    List<ComparisonColumn> columns,
    Set<String> restrictedFields,
    String approvedSortField,
    int maxCompareCount,
    String redactionProfile
) {
    public ComparisonViewConfig {
        if (viewId == null || viewId.isBlank() || version == null || version.isBlank()) {
            throw new QuoteCreateException("POLICY_NOT_SATISFIED", "Comparison view id and version are required");
        }
        if (columns == null || columns.isEmpty()) {
            throw new QuoteCreateException("POLICY_NOT_SATISFIED", "Comparison columns must come from approved configuration");
        }
        columns = columns.stream().sorted(Comparator.comparingInt(ComparisonColumn::order)).toList();
        restrictedFields = restrictedFields == null ? Set.of() : Set.copyOf(restrictedFields);
        redactionProfile = redactionProfile == null || redactionProfile.isBlank() ? "configured-redaction-profile" : redactionProfile;
        if (maxCompareCount < 1) {
            throw new QuoteCreateException("POLICY_NOT_SATISFIED", "Comparison max count must come from approved configuration");
        }
    }
}
