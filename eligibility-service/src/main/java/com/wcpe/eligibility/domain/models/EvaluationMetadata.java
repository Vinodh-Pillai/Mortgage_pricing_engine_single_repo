package com.wcpe.eligibility.domain.models;

import java.util.List;
import java.util.Map;

/**
 * Envelope-level metadata returned with eligibility responses per LLD.
 */
public record EvaluationMetadata(
    String engineVersion,
    boolean syntheticFixtureNotice,
    List<String> determinismInputs
) {}
