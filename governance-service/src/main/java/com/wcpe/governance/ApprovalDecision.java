package com.wcpe.governance;

import java.time.Instant;

public record ApprovalDecision(
    String decisionId,
    String approvalRequestId,
    String decision,
    String approverId,
    String approverGroup,
    String reasonCode,
    String comments,
    Instant decidedAt) {}
