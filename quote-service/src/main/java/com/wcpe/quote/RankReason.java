package com.wcpe.quote;

public record RankReason(
    String criterionId,
    String reasonType,
    String description,
    String evidence
) {
}
