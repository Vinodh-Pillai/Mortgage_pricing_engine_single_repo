export type QualityValidationStage = {
  stageId: string;
  label: string;
  status: string;
  timestampLabel: string;
};

export type QualityBlocker = {
  blockerId: string;
  severity: string;
  reasonClass: string;
  owner: string;
  status: string;
  summary: string;
};

export type QualityValidationRun = {
  runId: string;
  status: string;
  loopStatus: string;
  nextAction: string;
  stages: QualityValidationStage[];
  openBlockers: QualityBlocker[];
  evidencePaths: string[];
};

export type QualityReadinessStatus = {
  readinessStatus: string;
  deploymentDisabled: boolean;
  blockList: string[];
  signoffRefs: string[];
  dependencyChecks: string[];
  evidenceSetCompleteness: string;
};

export type QualityDriftMetric = {
  metricName: string;
  severity: string;
  deviationLabel: string;
};

export type QualityDriftSummary = {
  metricFamily: string;
  window: string;
  windowBaseline: string;
  affectedProducts: string[];
  cacheStaleness: string;
  lockoutReason: string;
  metrics: QualityDriftMetric[];
};

export type QualityFairnessSummary = {
  protectedClassDimensions: string[];
  redacted: boolean;
  sampleCountsLabel: string;
  breachSeverity: string;
  escalationTarget: string;
  evidenceRefs: string[];
};

export type QualityIncident = {
  incidentId: string;
  severity: string;
  escalationTarget: string;
  incidentClass: string;
  playbookRef: string;
  lifecycleStage: string;
  evidencePackageId: string;
  impactedServices: string[];
};

export type QualityReplaySummary = {
  policySnapshotId: string;
  inputBundleRef: string;
  deterministicSeedRef: string;
  replayAvailable: boolean;
  blockedReason: string;
  replayModes: string[];
};

export type QualityContractConformance = {
  contractId: string;
  status: string;
  summary: string;
  failures: string[];
};

export type QualityEvidenceExport = {
  packageId: string;
  completenessStatus: string;
  redacted: boolean;
  evidenceRefs: string[];
  blockers: string[];
};

export type QualityDashboardView = {
  tenantContext: string;
  dependencyStatus: string;
  validationRun: QualityValidationRun;
  readiness: QualityReadinessStatus;
  drift: QualityDriftSummary;
  fairness: QualityFairnessSummary;
  incidents: QualityIncident[];
  replay: QualityReplaySummary;
  contracts: QualityContractConformance[];
  evidenceExport: QualityEvidenceExport;
  uiTraceId: string;
  events: string[];
  fallbackReason: string;
};

const qualityHeaders = {
  Accept: 'application/json',
  'X-Ui-Trace-Id': 'ql-s08-local-trace',
  'X-Tenant-Context': 'ui-preview-tenant',
  'X-Quality-Role': 'viewer',
};

export async function fetchQualityDashboard(fetchImpl: typeof fetch = fetch): Promise<QualityDashboardView> {
  const response = await fetchImpl('/api/v1/quality/dashboard', { headers: qualityHeaders });
  if (response.status >= 500) throw new Error('BFF quality analytics boundary is temporarily unavailable.');
  return (await response.json()) as QualityDashboardView;
}

export async function requestQualityEvidenceExport(fetchImpl: typeof fetch = fetch): Promise<QualityEvidenceExport> {
  const response = await fetchImpl('/api/v1/quality/evidence/export', { headers: qualityHeaders });
  if (response.status >= 500) throw new Error('BFF quality evidence export boundary is temporarily unavailable.');
  return (await response.json()) as QualityEvidenceExport;
}
