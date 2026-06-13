package com.wcpe.quote;

public record EligibilityFailure(String dimension, String code, String message, Object actualValue, Object requiredValue) {}
