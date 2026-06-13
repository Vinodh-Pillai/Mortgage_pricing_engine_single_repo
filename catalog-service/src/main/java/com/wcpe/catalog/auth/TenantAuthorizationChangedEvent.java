package com.wcpe.catalog.auth;

import java.util.UUID;

public record TenantAuthorizationChangedEvent(UUID tenantId) {
}
