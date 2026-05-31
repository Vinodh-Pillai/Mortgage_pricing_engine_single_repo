package com.wcpe.adjustment;

/**
 * Symbolic/mock adjustment factor fixture.
 */
public record AdjustmentFactor(
    String factorKey,
    double amount,
    String reason
) {}
