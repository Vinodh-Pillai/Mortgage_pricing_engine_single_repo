package com.wcpe.catalog.auth;

import java.time.Instant;

public record TenantProductAuthorizationPatch(String status, Instant expiresAt, String notes) {
}
