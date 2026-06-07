package com.wcpe.mladvisory;

public record AdvisoryReason(
    String reasonCode,
    int rank,
    String description,
    String direction,
    String featureRef,
    String sensitivityClass) {}
