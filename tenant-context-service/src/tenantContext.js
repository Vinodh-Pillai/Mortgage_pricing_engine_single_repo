const TENANT_ID_PATTERN = /^[A-Za-z0-9][A-Za-z0-9._-]{1,63}$/;
const TRACE_ID_PATTERN = /^[A-Za-z0-9][A-Za-z0-9._:-]{1,127}$/;

class TenantContextValidationError extends Error {
  constructor(code, message, details = {}) {
    super(message);
    this.name = "TenantContextValidationError";
    this.code = code;
    this.details = details;
  }
}

function normalizeRequiredString(value, fieldName, pattern) {
  if (typeof value !== "string") {
    throw new TenantContextValidationError("TENANT_CONTEXT_MALFORMED", `${fieldName} must be a string`, {
      field: fieldName,
    });
  }

  const normalized = value.trim();
  if (normalized.length === 0) {
    throw new TenantContextValidationError("TENANT_CONTEXT_MISSING", `${fieldName} is required`, {
      field: fieldName,
    });
  }

  if (!pattern.test(normalized)) {
    throw new TenantContextValidationError("TENANT_CONTEXT_MALFORMED", `${fieldName} is malformed`, {
      field: fieldName,
    });
  }

  return normalized;
}

function normalizeTenantContext(input) {
  if (input == null || typeof input !== "object" || Array.isArray(input)) {
    throw new TenantContextValidationError("TENANT_CONTEXT_MISSING", "tenant context input is required");
  }

  const tenantId = normalizeRequiredString(input.tenantId, "tenantId", TENANT_ID_PATTERN);
  const requestId = normalizeRequiredString(input.requestId, "requestId", TRACE_ID_PATTERN);
  const traceId = normalizeRequiredString(input.traceId, "traceId", TRACE_ID_PATTERN);

  return Object.freeze({
    tenantId,
    request: Object.freeze({
      requestId,
      traceId,
    }),
  });
}

module.exports = {
  TenantContextValidationError,
  normalizeTenantContext,
};
