package com.wcpe.quote;

public record ComparisonColumn(String field, String label, int order) {
    public ComparisonColumn {
        if (field == null || field.isBlank()) {
            throw new QuoteCreateException("POLICY_NOT_SATISFIED", "Comparison column field is required");
        }
        if (label == null || label.isBlank()) {
            throw new QuoteCreateException("POLICY_NOT_SATISFIED", "Comparison column label is required");
        }
    }
}
