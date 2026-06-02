package com.wcpe.eligibility.domain.models;

import java.math.BigDecimal;
import java.util.List;

public record FicoLtvMatrixConfig(
    String matrixSetId,
    String productFamily,
    String investorCode,
    String channel,
    String status,
    int version,
    List<FicoLtvMatrixRow> rows
) {}
