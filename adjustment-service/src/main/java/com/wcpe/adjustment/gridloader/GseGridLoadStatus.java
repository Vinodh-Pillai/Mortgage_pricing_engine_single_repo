package com.wcpe.adjustment.gridloader;

import java.time.Instant;

public record GseGridLoadStatus(String investorCode, String ruleBookVersion, int cellCount, String status, Instant lastLoad,
                                String ruleBookHash, int warningCount, String errorMessage) {}
