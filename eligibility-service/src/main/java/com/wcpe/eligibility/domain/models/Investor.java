package com.wcpe.eligibility.domain.models;

import java.time.LocalDate;
import java.util.List;

public record Investor(
    String investorCode,
    String name,
    String status,
    List<String> productFamilies,
    LocalDate effectiveFrom,
    LocalDate effectiveTo
) {}
