export type TenantContextTrace = {
  tenantIdRef: string;
  correlationIdRef: string;
  idempotencyKeyRef: string;
  eventEnvelopeRef: string;
  auditRef: string;
  replayHashRef: string;
};

export type TenantPlatformControl = {
  controlId: string;
  label: string;
  status: string;
  guidance: string;
  evidenceRefs: string[];
  blockers: string[];
};

export type TenantPlatformBlocker = {
  code: string;
  owner: string;
  message: string;
};

export type TenantPlatformCoverageView = {
  tenantContext: string;
  dependencyStatus: string;
  uiTraceId: string;
  trace: TenantContextTrace;
  controls: TenantPlatformControl[];
  blockers: TenantPlatformBlocker[];
  events: string[];
  fallbackReason: string;
};

const tenantPlatformHeaders = {
  Accept: 'application/json',
  'X-Ui-Trace-Id': 'tc-s08-local-trace',
  'X-Tenant-Context': 'ui-preview-tenant',
};

export async function fetchTenantPlatformCoverage(fetchImpl: typeof fetch = fetch): Promise<TenantPlatformCoverageView> {
  const response = await fetchImpl('/api/v1/platform/tenant-context', { headers: tenantPlatformHeaders });
  if (response.status >= 500) throw new Error('Tenant platform diagnostics are temporarily unavailable.');
  return (await response.json()) as TenantPlatformCoverageView;
}
