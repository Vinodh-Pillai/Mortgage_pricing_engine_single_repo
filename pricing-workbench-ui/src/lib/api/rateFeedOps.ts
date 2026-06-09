export type RateFeedWorkflowStep = {
  stepId: string;
  label: string;
  status: string;
  sourceBoundary: string;
  auditRef: string;
  resultHashRef: string;
};

export type RateFeedGridBlocker = {
  rowRef: string;
  fieldName: string;
  severity: string;
  blockerCode: string;
  sourceReference: string;
  resolutionState: string;
};

export type RateFeedOperationsView = {
  tenantContext: string;
  dependencyStatus: string;
  workflowSteps: RateFeedWorkflowStep[];
  rowBlockers: RateFeedGridBlocker[];
  sourceReferences: string[];
  replayEvidence: string[];
  actionsDisabled: boolean;
  fallbackReason: string;
  uiTraceId: string;
  events: string[];
};

const rateFeedOpsHeaders = {
  Accept: 'application/json',
  'X-Ui-Trace-Id': 'rf-s03-local-trace',
  'X-Tenant-Context': 'ui-preview-tenant',
};

export async function fetchRateFeedOperations(fetchImpl: typeof fetch = fetch): Promise<RateFeedOperationsView> {
  const response = await fetchImpl('/api/v1/ops/rate-feeds', { headers: rateFeedOpsHeaders });
  if (response.status >= 500) throw new Error('Rate feed operations boundary is temporarily unavailable.');
  return (await response.json()) as RateFeedOperationsView;
}
