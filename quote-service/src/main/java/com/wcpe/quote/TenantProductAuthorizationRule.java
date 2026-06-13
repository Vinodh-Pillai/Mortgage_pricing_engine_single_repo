package com.wcpe.quote;

public record TenantProductAuthorizationRule(
    String productCode,
    String investorCode,
    String channelCode,
    String status,
    Long expiresAtEpochMillis
) {
}
