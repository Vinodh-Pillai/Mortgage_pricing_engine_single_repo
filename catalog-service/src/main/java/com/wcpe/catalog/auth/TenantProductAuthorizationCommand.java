package com.wcpe.catalog.auth;

import java.time.Instant;
import java.util.UUID;

public record TenantProductAuthorizationCommand(
    UUID tenantId,
    String productCode,
    String investorCode,
    String channelCode,
    String status,
    Instant expiresAt,
    String notes
) {
}
