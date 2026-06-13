package com.wcpe.adjustment.overlay;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OverlayInputs(
    UUID tenantId,
    String investorCode,
    String channelCode,
    String productFamily,
    String loanPurpose,
    String occupancy,
    String propertyType,
    String stateCode,
    String countyCode,
    BigDecimal loanAmount,
    boolean escrowWaived,
    String armCaps,
    String buydownType,
    boolean highBalance,
    boolean jumbo,
    Integer loanTermMonths,
    Instant quoteDate
) {
    public boolean triggerMatches(OverlayPolicyType type) {
        return switch (type) {
            case ESCROW_WAIVER -> escrowWaived;
            case ARM_CAPS -> armCaps != null && !armCaps.isBlank();
            case BUYDOWN -> buydownType != null && !buydownType.isBlank();
            case HIGH_BALANCE -> highBalance;
            case JUMBO -> jumbo;
            case ODD_TERM -> loanTermMonths != null;
        };
    }
}
