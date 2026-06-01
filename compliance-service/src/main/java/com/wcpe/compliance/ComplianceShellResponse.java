package com.wcpe.compliance;

import java.util.List;

public record ComplianceShellResponse(
    String requestId,
    String status,
    List<String> decisions,
    List<String> messages,
    List<String> evidenceRefs,
    String ruleSetRef) {}
