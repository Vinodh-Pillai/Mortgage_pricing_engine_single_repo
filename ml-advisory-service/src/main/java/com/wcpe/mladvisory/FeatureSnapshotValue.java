package com.wcpe.mladvisory;

public record FeatureSnapshotValue(
    String featureName,
    String featureType,
    String redactedValue,
    String sensitivityClass,
    String sourceSystem,
    String sourceField,
    boolean included,
    String exclusionReason,
    String valueHash,
    String businessJustification) {}
