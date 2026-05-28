package com.wcpe.eligibility.domain.models;

import java.util.List;
import java.util.Map;

/**
 * LLD-mandated eligibility status enum.
 * PII-01 golden fixtures include a WARNING terminal status when evaluation can
 * complete with non-blocking insufficient-data decisions.
 */
public enum EligibilityStatus {
    ELIGIBLE,
    INELIGIBLE,
    WARNING,
    CANNOT_DECIDE,
    OUT_OF_SCOPE
}
