package com.wcpe.mladvisory;

public record ExplanationDriver(
    int rank,
    String featureLabel,
    String direction,
    String relativeImpact,
    String visibilityClass,
    boolean suppressed,
    String suppressionReason) {}
