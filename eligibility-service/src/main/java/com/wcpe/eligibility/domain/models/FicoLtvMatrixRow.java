package com.wcpe.eligibility.domain.models;

import java.math.BigDecimal;
import java.util.UUID;

public record FicoLtvMatrixRow(
    UUID matrixRowId,
    UUID matrixSetId,
    int ficoMin,
    int ficoMax,
    BigDecimal maxLtv,
    BigDecimal maxCltv,
    String loanPurpose,
    String occupancyType,
    String propertyType,
    int unitsMin,
    int unitsMax,
    String documentationType,
    String ausType,
    String severityIfMissingFico,
    String reasonCode,
    String rowHash
) {}
