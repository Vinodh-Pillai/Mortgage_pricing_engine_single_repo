package com.wcpe.quote;

import java.util.UUID;

public record TenantAuthorizationChangedEvent(UUID tenantId) {
}
