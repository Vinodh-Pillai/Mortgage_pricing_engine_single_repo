export type AuditReplayRecordSummary = {
  eventId: string;
  subjectType: string;
  subjectId: string;
  action: string;
  hashIntegrity: string;
  redactionProfile: string;
  retentionState: string;
  legalHold: boolean;
  exportEligibility: string;
  evidenceRefs: string[];
};

export type AuditReplayRunSummary = {
  runId: string;
  replayType: string;
  subjectId: string;
  status: string;
  originalHash: string;
  replayHash: string;
  diffs: string[];
  missingDependencyBlockers: string[];
  versionRefs: string[];
};

export type AuditReplayExportSummary = {
  exportId: string;
  status: string;
  redactionProfile: string;
  retentionUntil: string;
  legalHold: boolean;
  downloadEligible: boolean;
  manifestHash: string;
  blockers: string[];
};

export type AuditReplayContractRef = {
  contractId: string;
  route: string;
  preservedDecision: string;
};

export type AuditReplayWorkbenchView = {
  tenantContext: string;
  dependencyStatus: string;
  uiTraceId: string;
  records: AuditReplayRecordSummary[];
  replayRuns: AuditReplayRunSummary[];
  exportSummary: AuditReplayExportSummary;
  contractRefs: AuditReplayContractRef[];
  blockers: string[];
  events: string[];
  fallbackReason: string;
};

const auditReplayHeaders = {
  Accept: 'application/json',
  'X-Ui-Trace-Id': 'ar-s10-local-trace',
  'X-Tenant-Context': 'ui-preview-tenant',
};

export async function fetchAuditReplayWorkbench(fetchImpl: typeof fetch = fetch): Promise<AuditReplayWorkbenchView> {
  const response = await fetchImpl('/api/v1/audit-replay/workbench', { headers: auditReplayHeaders });
  if (response.status >= 500) throw new Error('BFF audit replay workbench boundary is temporarily unavailable.');
  return (await response.json()) as AuditReplayWorkbenchView;
}
