package com.wcpe.adjustment.gridloader;

import com.wcpe.adjustment.FicoLtvLlpaEvaluator.BoundaryPolicy;
import java.math.BigDecimal;

final class GridBandParser {
    private GridBandParser() {}

    static FicoRange parseFico(String band) {
        String normalized = require(band).replace(" ", "");
        if (normalized.endsWith("+")) return new FicoRange(Integer.parseInt(normalized.substring(0, normalized.length() - 1)), 850);
        if (normalized.startsWith("<=")) return new FicoRange(0, Integer.parseInt(normalized.substring(2)));
        if (normalized.startsWith("<")) return new FicoRange(0, Integer.parseInt(normalized.substring(1)) - 1);
        String[] parts = normalized.split("-");
        if (parts.length == 2) return new FicoRange(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        int value = Integer.parseInt(normalized);
        return new FicoRange(value, value);
    }

    static LtvRange parseLtv(String band) {
        String normalized = require(band).replace("%", "").replace(" ", "");
        if (normalized.startsWith("<=")) return new LtvRange(new BigDecimal("0.00"), new BigDecimal(normalized.substring(2)), BoundaryPolicy.MIN_INCLUSIVE_MAX_INCLUSIVE);
        if (normalized.startsWith("<")) return new LtvRange(new BigDecimal("0.00"), new BigDecimal(normalized.substring(1)), BoundaryPolicy.MIN_INCLUSIVE_MAX_INCLUSIVE);
        if (normalized.startsWith(">")) return new LtvRange(new BigDecimal(normalized.substring(1)), new BigDecimal("999.99"), BoundaryPolicy.MIN_EXCLUSIVE_MAX_INCLUSIVE);
        String[] parts = normalized.split("-");
        if (parts.length == 2) {
            BoundaryPolicy policy = parts[0].contains(".") ? BoundaryPolicy.MIN_INCLUSIVE_MAX_INCLUSIVE : BoundaryPolicy.MIN_EXCLUSIVE_MAX_INCLUSIVE;
            return new LtvRange(new BigDecimal(parts[0]), new BigDecimal(parts[1]), policy);
        }
        BigDecimal value = new BigDecimal(normalized);
        return new LtvRange(value, value, BoundaryPolicy.MIN_INCLUSIVE_MAX_INCLUSIVE);
    }

    private static String require(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("band is required");
        return value.trim();
    }

    record FicoRange(int min, int max) {}
    record LtvRange(BigDecimal min, BigDecimal max, BoundaryPolicy boundaryPolicy) {}
}
