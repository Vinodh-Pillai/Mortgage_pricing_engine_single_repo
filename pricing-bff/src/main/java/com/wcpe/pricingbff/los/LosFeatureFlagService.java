package com.wcpe.pricingbff.los;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
class LosFeatureFlagService {
  private final Map<String, LoanPassTenantFlags> flagsByTenant = new ConcurrentHashMap<>();

  LoanPassTenantFlags lookup(String tenantId) {
    String normalizedTenant = normalizeTenant(tenantId);
    return flagsByTenant.getOrDefault(normalizedTenant, LoanPassTenantFlags.failClosed(normalizedTenant));
  }

  LoanPassTenantFlags configure(LoanPassTenantFlags flags) {
    if (flags == null || blank(flags.tenantId())) {
      throw new LosValidationException("TENANT_FEATURE_FLAGS_REQUIRED", "tenantId is required for LoanPass tenant feature flags");
    }
    LoanPassTenantFlags normalized = flags.normalized();
    flagsByTenant.put(normalized.tenantId(), normalized);
    return normalized;
  }

  void clear() {
    flagsByTenant.clear();
  }

  private static String normalizeTenant(String tenantId) {
    return blank(tenantId) ? "unknown" : tenantId.trim();
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  record LoanPassTenantFlags(
      String tenantId,
      boolean loanPassCompatibilityEnabled,
      boolean strictMappingEnabled,
      boolean callbackDeliveryEnabled,
      String configRef,
      String auditRef,
      int version,
      Instant updatedAt) {

    LoanPassTenantFlags {
      tenantId = normalizeTenant(tenantId);
      configRef = blank(configRef) ? "tenant-feature-flags:" + tenantId + ":v" + Math.max(1, version) : configRef.trim();
      auditRef = blank(auditRef) ? "tenant-feature-flags:audit:" + tenantId + ":v" + Math.max(1, version) : auditRef.trim();
      version = Math.max(0, version);
      updatedAt = updatedAt == null ? Instant.EPOCH : updatedAt;
    }

    static LoanPassTenantFlags failClosed(String tenantId) {
      String normalizedTenant = normalizeTenant(tenantId);
      return new LoanPassTenantFlags(normalizedTenant, false, true, false,
          "tenant-feature-flags:missing:" + normalizedTenant,
          "tenant-feature-flags:audit:missing:" + normalizedTenant,
          0, Instant.EPOCH);
    }

    LoanPassTenantFlags normalized() {
      return new LoanPassTenantFlags(tenantId, loanPassCompatibilityEnabled, strictMappingEnabled,
          callbackDeliveryEnabled, configRef, auditRef, version, updatedAt);
    }
  }
}
