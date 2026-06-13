package com.wcpe.adjustment.gridloader;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

public record GseGridRow(
    int sourceRow,
    String investorCode,
    String ficoBand,
    String ltvBand,
    String loanPurpose,
    String propertyType,
    String occupancy,
    int units,
    BigDecimal llpaBps,
    Instant effectiveStart,
    Instant effectiveEnd,
    String versionLabel
) {
    public GseGridRow {
        if (sourceRow < 1) throw new IllegalArgumentException("sourceRow must be positive");
        investorCode = required(investorCode, "investorCode");
        ficoBand = required(ficoBand, "ficoBand");
        ltvBand = required(ltvBand, "ltvBand");
        loanPurpose = required(loanPurpose, "loanPurpose").toUpperCase(Locale.ROOT);
        propertyType = required(propertyType, "propertyType").toUpperCase(Locale.ROOT);
        occupancy = required(occupancy, "occupancy").toUpperCase(Locale.ROOT);
        if (units < 1) throw new IllegalArgumentException("units must be positive");
        Objects.requireNonNull(llpaBps, "llpaBps is required");
        Objects.requireNonNull(effectiveStart, "effectiveStart is required");
        versionLabel = required(versionLabel, "versionLabel");
    }

    boolean isCashOut() {
        return loanPurpose.contains("CASH_OUT");
    }

    boolean isCoreFicoLtv() {
        return !isCashOut() && ("SFR".equals(propertyType) || "SINGLE_FAMILY".equals(propertyType))
            && "PRIMARY".equals(occupancy) && units == 1;
    }

    boolean isPropertyOccupancyAddOn() {
        return !isCashOut() && !isCoreFicoLtv();
    }

    String cellKey() {
        return String.join("|", investorCode, ficoBand, ltvBand, loanPurpose, propertyType, occupancy, Integer.toString(units));
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
