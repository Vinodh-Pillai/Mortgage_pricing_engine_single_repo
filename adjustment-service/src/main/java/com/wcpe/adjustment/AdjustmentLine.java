package com.wcpe.adjustment;

import java.util.List;

/**
 * Single adjustment line in the calculation result. The first four fields keep
 * the original service shape while the later fields carry real LLPA rule audit data.
 */
public record AdjustmentLine(
    String factorKey,
    double amount,
    String reason,
    String source,
    String outputType,
    String ruleId,
    String sourceRef,
    boolean applied,
    String label,
    String auditRef,
    List<String> warnings
) {
    public AdjustmentLine(String factorKey, double amount, String reason, String source) {
        this(factorKey, amount, reason, source, "POINTS_DELTA", null, source, true, null, source, List.of());
    }

    public AdjustmentLine {
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
    }
}
