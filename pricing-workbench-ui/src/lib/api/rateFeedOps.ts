export type RateFeedWorkflowStep = {
  stepId: string;
  label: string;
  status: string;
  sourceBoundary: string;
  auditRef: string;
  resultHashRef: string;
  inputRef?: string;
  outputRef?: string;
  timingRef?: string;
  errorRefs?: string[];
  logRefs?: string[];
  actionPermitted?: boolean;
};

export type RateFeedGridBlocker = {
  rowRef: string;
  fieldName: string;
  severity: string;
  blockerCode: string;
  sourceReference: string;
  resolutionState: string;
  rowDataRefs?: string[];
  expectedRef?: string;
  actualRef?: string;
  remediationRef?: string;
  resolutionActions?: string[];
};

export type RateFeedHealthSummary = {
  totalFeeds: number | string;
  healthy: number | string;
  degraded: number | string;
  failed: number | string;
  lastSuccessfulIngestRef: string;
  feeds: RateFeedHealthFeed[];
};

export type RateFeedHealthFeed = {
  feedId: string;
  feedName: string;
  investorRef: string;
  lastRunRef: string;
  status: string;
  freshnessRef: string;
  rowCountRef: string;
  blockerCountRef: string;
  detailTargetRef: string;
};

export type RateFeedReplayRun = {
  runId: string;
  feedName: string;
  runDateRef: string;
  versionRefs: string[];
};

export type RateFeedReplayDiff = {
  diffId: string;
  changeType: string;
  rowRef: string;
  fieldName: string;
  originalRef: string;
  replayRef: string;
  sourceRef: string;
};

export type RateFeedReplayEvidenceView = {
  selectedRunId: string;
  runs: RateFeedReplayRun[];
  diffs: RateFeedReplayDiff[];
  missingDependencyBlockers: string[];
  versionRefs: string[];
  exportAvailable: boolean;
  exportRef?: string;
};

export type RateFeedOperationsView = {
  tenantContext: string;
  dependencyStatus: string;
  lastUpdatedRef?: string;
  feedHealth?: RateFeedHealthSummary;
  workflowSteps: RateFeedWorkflowStep[];
  rowBlockers: RateFeedGridBlocker[];
  sourceReferences: string[];
  replayEvidence: string[] | RateFeedReplayEvidenceView;
  actionsDisabled: boolean;
  fallbackReason: string;
  uiTraceId: string;
  events: string[];
};

export async function fetchRateFeedOperations(
  tenantContext = 'ui-preview-tenant',
  fetchImpl: typeof fetch = fetch,
): Promise<RateFeedOperationsView> {
  const response = await fetchImpl('/api/v1/ops/rate-feeds', {
    headers: {
      Accept: 'application/json',
      'X-Ui-Trace-Id': 'rf-s18-local-trace',
      'X-Tenant-Context': tenantContext,
    },
  });
  if (response.status >= 500) throw new Error('Rate feed operations boundary is temporarily unavailable.');
  return (await response.json()) as RateFeedOperationsView;
}
