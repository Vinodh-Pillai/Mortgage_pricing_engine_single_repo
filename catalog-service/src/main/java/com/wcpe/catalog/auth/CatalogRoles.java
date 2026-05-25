package com.wcpe.catalog.auth;

import java.util.Map;
import java.util.Set;

public final class CatalogRoles {
    public static final Map<String, Set<String>> PERMISSION_MATRIX = Map.of(
        "READ_CATALOG", Set.of("CATALOG_ADMIN", "CATALOG_MANAGER", "CATALOG_READER", "CATALOG_WRITER", "CATALOG_APPROVER", "CATALOG_PUBLISHER"),
        "WRITE_CATALOG", Set.of("CATALOG_ADMIN", "CATALOG_MANAGER", "CATALOG_WRITER"),
        "APPROVE_CATALOG", Set.of("CATALOG_ADMIN", "CATALOG_MANAGER", "CATALOG_APPROVER"),
        "PUBLISH_CATALOG", Set.of("CATALOG_ADMIN", "CATALOG_MANAGER", "CATALOG_PUBLISHER"),
        "MANAGE_OVERLAY", Set.of("CATALOG_ADMIN", "CATALOG_MANAGER"),
        "ROLLBACK_CATALOG", Set.of("CATALOG_ADMIN")
    );
}
