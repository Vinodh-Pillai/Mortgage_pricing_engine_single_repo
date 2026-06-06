package com.wcpe.adjustment;

import java.math.BigDecimal;
import java.util.Objects;

public record AdjustmentCapFloor(BigDecimal capAmount, BigDecimal floorAmount) {
    public AdjustmentCapFloor {
        if (capAmount != null) {
            Objects.requireNonNull(capAmount, "capAmount is required when configured");
        }
        if (floorAmount != null) {
            Objects.requireNonNull(floorAmount, "floorAmount is required when configured");
        }
        if (capAmount != null && floorAmount != null && floorAmount.compareTo(capAmount) > 0) {
            throw new IllegalArgumentException("floorAmount cannot exceed capAmount");
        }
    }

    public BigDecimal apply(BigDecimal rawAmount) {
        Objects.requireNonNull(rawAmount, "rawAmount is required");
        if (isCapApplied(rawAmount)) {
            return capAmount;
        }
        if (isFloorApplied(rawAmount)) {
            return floorAmount;
        }
        return rawAmount;
    }

    public boolean isCapApplied(BigDecimal rawAmount) {
        Objects.requireNonNull(rawAmount, "rawAmount is required");
        return capAmount != null && rawAmount.compareTo(capAmount) > 0;
    }

    public boolean isFloorApplied(BigDecimal rawAmount) {
        Objects.requireNonNull(rawAmount, "rawAmount is required");
        return floorAmount != null && rawAmount.compareTo(floorAmount) < 0;
    }

    private static String requireText(String value, String message) {
        String normalized = Objects.requireNonNull(value, message).trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }
}
