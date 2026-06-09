export type PerformanceSignal = {
  signalId: string;
  label: string;
  freshness: string;
  source: string;
  sourceRef: string;
  evidenceRefs: string[];
};

export type PerformanceBlocker = {
  code: string;
  owner: string;
  message: string;
};

export type PerformanceSignalGroup = {
  serviceName: string;
  tenantContext: string;
  correlationId: string;
  freshness: string;
  signals: PerformanceSignal[];
  blockers: PerformanceBlocker[];
};

export type PerformanceImpact = {
  impactCode: string;
  summary: string;
  source: string;
  recoveryOwner: string;
  runbookRef: string;
};

export type PerformanceDashboardView = {
  tenantContext: string;
  dependencyStatus: string;
  signalGroups: PerformanceSignalGroup[];
  impacts: PerformanceImpact[];
  evidenceLinks: string[];
  blockers: PerformanceBlocker[];
  actionsDisabled: boolean;
  fallbackReason: string;
  uiTraceId: string;
  events: string[];
};

const performanceHeaders = {
  Accept: 'application/json',
  'X-Ui-Trace-Id': 'perf-s09-local-trace',
  'X-Tenant-Context': 'ui-preview-tenant',
};

export async function fetchPerformanceDashboard(fetchImpl: typeof fetch = fetch): Promise<PerformanceDashboardView> {
  const response = await fetchImpl('/api/v1/ops/performance', { headers: performanceHeaders });
  if (response.status >= 500) throw new Error('Performance dashboard boundary is temporarily unavailable.');
  return (await response.json()) as PerformanceDashboardView;
}
