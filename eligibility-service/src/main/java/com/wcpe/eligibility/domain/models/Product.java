package com.wcpe.eligibility.domain.models;

import java.time.LocalDate;
import java.util.List;

public record Product(
    String productCode,
    String name,
    String productFamily,
    List<String> allowedChannels,
    List<String> allowedStates,
    LocalDate effectiveFrom,
    LocalDate effectiveTo
) {}
