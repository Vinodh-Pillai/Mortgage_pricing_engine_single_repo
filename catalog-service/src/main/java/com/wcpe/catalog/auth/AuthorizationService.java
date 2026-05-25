package com.wcpe.catalog.auth;

import java.util.Arrays;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class AuthorizationService {
    public void authorize(String permission, String rolesHeader) {
        Set<String> allowed = CatalogRoles.PERMISSION_MATRIX.getOrDefault(permission, Set.of());
        if (rolesHeader == null || rolesHeader.isBlank()) {
            throw new IllegalArgumentException("RBAC_MISSING_ROLES_HEADER");
        }
        Set<String> userRoles = Arrays.stream(rolesHeader.split(","))
            .map(String::trim).collect(java.util.stream.Collectors.toSet());
        if (userRoles.stream().noneMatch(allowed::contains)) {
            throw new IllegalArgumentException("RBAC_UNAUTHORIZED_" + permission);
        }
    }

    public void enforceSoD(String actorId, String approverRef, String operation) {
        if (operation == null) return;
        String[] parts = operation.split(":");
        if (parts.length == 2 && parts[0].equals("SUBMITTED")) {
            String submittedBy = parts[1];
            if (actorId != null && actorId.equals(submittedBy)) {
                throw new IllegalArgumentException("SEPARATION_OF_DUTIES_VIOLATION");
            }
        }
    }
}
