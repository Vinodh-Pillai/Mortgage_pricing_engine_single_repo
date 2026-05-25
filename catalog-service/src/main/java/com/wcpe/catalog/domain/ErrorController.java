package com.wcpe.catalog.domain;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ErrorController {

    @ExceptionHandler(CatalogException.class)
    ResponseEntity<Map<String, Object>> handleCatalogException(CatalogException ex) {
        String code = ex.getMessage();
        HttpStatus status;
        switch (code) {
            case "IDEMPOTENCY_CONFLICT":
                status = HttpStatus.CONFLICT;
                break;
            case "CATALOG_NOT_EDITABLE":
            case "CATALOG_VERSION_CONFLICT":
            case "INVALID_CATALOG_STATUS_TRANSITION":
            case "PRODUCT_CODE_DUPLICATE":
            case "INVESTOR_CODE_DUPLICATE":
            case "PRODUCT_CODE_REQUIRED":
            case "PRODUCT_NAME_REQUIRED":
            case "PRODUCT_FAMILY_REQUIRED":
            case "INVESTOR_CODE_REQUIRED":
            case "INVESTOR_NAME_REQUIRED":
            case "INVESTOR_PRODUCT_UNKNOWN":
            case "REFERENCE_CODE_REQUIRED":
            case "REFERENCE_LABEL_REQUIRED":
            case "EFFECTIVE_FROM_REQUIRED":
            case "INVALID_STATE_CODE":
            case "INVALID_COUNTY_FIPS":
            case "NO_ACTIVE_PRODUCTS":
            case "NO_ACTIVE_INVESTORS":
            case "NO_PUBLISHED_CATALOG":
            case "SNAPSHOT_NOT_FOUND":
            case "CATALOG_PRODUCTS_REQUIRED":
            case "CATALOG_INVESTORS_REQUIRED":
                status = HttpStatus.BAD_REQUEST;
                break;
            default:
                status = HttpStatus.UNPROCESSABLE_ENTITY;
                break;
        }
        return ResponseEntity.status(status).body(Map.of("code", code, "message", code));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        String code = ex.getMessage();
        HttpStatus status;
        switch (code) {
            case "RBAC_MISSING_ROLES_HEADER":
                status = HttpStatus.UNAUTHORIZED;
                break;
            case "SEPARATION_OF_DUTIES_VIOLATION":
                status = HttpStatus.FORBIDDEN;
                break;
            default:
                if (code != null && code.startsWith("RBAC_UNAUTHORIZED_")) {
                    status = HttpStatus.FORBIDDEN;
                } else if (code != null && code.endsWith("_REQUIRED")) {
                    status = HttpStatus.BAD_REQUEST;
                } else if (code != null && (code.equals("OVERLAY_CONFLICT") || code.equals("EXPIRY_DATE_INVALID"))) {
                    status = HttpStatus.CONFLICT;
                } else {
                    status = HttpStatus.INTERNAL_SERVER_ERROR;
                }
                break;
        }
        return ResponseEntity.status(status).body(Map.of("code", code, "message", code));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of("code", "INTERNAL_ERROR", "message", ex.getClass().getSimpleName()));
    }
}
