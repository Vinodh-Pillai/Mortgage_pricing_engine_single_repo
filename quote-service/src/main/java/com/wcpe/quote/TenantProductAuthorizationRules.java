package com.wcpe.quote;

import java.util.List;
import java.util.UUID;

public interface TenantProductAuthorizationRules {
    List<TenantProductAuthorizationRule> authorizationsFor(UUID tenantId);

    default void invalidate(UUID tenantId) {
    }
}
