package com.wcpe.eligibility.domain.models;

import java.time.LocalDate;
import java.util.UUID;

public record PolicyVersion(
    UUID id,
    String tenantId,
    int version,
    String name,
    String status,
    LocalDate effectiveFrom,
    LocalDate effectiveTo
) {}
