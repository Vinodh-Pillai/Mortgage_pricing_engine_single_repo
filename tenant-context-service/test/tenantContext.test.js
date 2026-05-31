const assert = require("node:assert/strict");
const test = require("node:test");

const {
  TenantContextValidationError,
  normalizeTenantContext,
} = require("../src/tenantContext");

test("normalizes valid tenant context with traceable request context", () => {
  const context = normalizeTenantContext({
    tenantId: " tenant-alpha ",
    requestId: " request-123 ",
    traceId: " trace:abc-123 ",
  });

  assert.deepEqual(context, {
    tenantId: "tenant-alpha",
    request: {
      requestId: "request-123",
      traceId: "trace:abc-123",
    },
  });
  assert.ok(Object.isFrozen(context));
  assert.ok(Object.isFrozen(context.request));
});

test("fails deterministically when tenant context input is missing", () => {
  assert.throws(
    () => normalizeTenantContext(undefined),
    (error) => error instanceof TenantContextValidationError && error.code === "TENANT_CONTEXT_MISSING",
  );
});

test("fails deterministically when tenant context input is malformed", () => {
  assert.throws(
    () => normalizeTenantContext({ tenantId: "tenant alpha", requestId: "request-1", traceId: "trace-1" }),
    (error) => error instanceof TenantContextValidationError && error.code === "TENANT_CONTEXT_MALFORMED",
  );
});

test("keeps tenant context request-scoped without static shared state", () => {
  const first = normalizeTenantContext({ tenantId: "tenant-one", requestId: "request-1", traceId: "trace-1" });
  const second = normalizeTenantContext({ tenantId: "tenant-two", requestId: "request-2", traceId: "trace-2" });

  assert.notEqual(first, second);
  assert.equal(first.tenantId, "tenant-one");
  assert.equal(second.tenantId, "tenant-two");
  assert.equal(first.request.requestId, "request-1");
  assert.equal(second.request.requestId, "request-2");
});

test("does not invent default tenant values", () => {
  assert.throws(
    () => normalizeTenantContext({ requestId: "request-1", traceId: "trace-1" }),
    (error) => error instanceof TenantContextValidationError && error.code === "TENANT_CONTEXT_MALFORMED",
  );
});
