package com.wcpe.eligibility.domain.models;

import java.util.List;
import java.util.Map;

/**
 * LLD-mandated eligibility status enum.
 * Exactly four values; WARNING is forbidden.
 */
public enum EligibilityStatus {
    ELIGIBLE,
    INELIGIBLE,
    CANNOT_DECIDE,
    OUT_OF_SCOPE
}
