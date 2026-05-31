package com.wcpe.adjustment;

/**
 * Single adjustment line in the calculation result.
 */
public record AdjustmentLine(
    String factorKey,
    double amount,
    String reason,
    String source
) {}
