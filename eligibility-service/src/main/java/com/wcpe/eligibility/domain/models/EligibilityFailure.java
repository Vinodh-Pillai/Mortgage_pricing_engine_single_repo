package com.wcpe.eligibility.domain.models;

public record EligibilityFailure(String dimension, String code, String message, Object actualValue, Object requiredValue) {}
