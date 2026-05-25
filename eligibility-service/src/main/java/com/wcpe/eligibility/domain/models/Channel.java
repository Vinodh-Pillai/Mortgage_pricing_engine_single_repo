package com.wcpe.eligibility.domain.models;

import java.time.LocalDate;

public record Channel(
    String channelCode,
    String name,
    String status,
    LocalDate effectiveFrom,
    LocalDate effectiveTo
) {}
