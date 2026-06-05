package com.wcpe.mladvisory;

public record FeatureInput(
    String name,
    String type,
    String value,
    String sensitivityClass,
    String sourceSystem,
    String sourceField,
    String businessJustification) {}
