export type AdminTraceMetadata = {
  traceId: string;
  artifactId: string;
  policyVersion: string;
  environment: string;
  signerMetadata: string;
};

export type PolicyVersionSummary = {
  versionId: string;
  owner: string;
  status: string;
  environmentMapping: string;
  parentVersionId: string;
  hashSignature: string;
  diffImpacts: string[];
};

export type FeatureFlagSummary = {
  flagId: string;
  environmentTarget: string;
  enabled: boolean;
  unresolvedFlags: string[];
  activationDisabled: boolean;
  emergencyToggleGate: string;
};

export type MarketRuleSummary = {
  ruleId: string;
  ruleType: string;
  stagingStatus: string;
  missingRequiredFields: string[];
  promotionDisabled: boolean;
  completenessGate: string;
};

export type ChangeRequestSummary = {
  requestId: string;
  requestType: string;
  state: string;
  riskLevel: string;
  owner: string;
  requiredStateSequence: string[];
  promotionDisabled: boolean;
  blockers: string[];
};

export type ReleaseGateSummary = {
  gateName: string;
  status: string;
  mandatory: boolean;
  artifactRef: string;
};

export type ReleaseCandidateReadiness = {
  candidateId: string;
  readinessStatus: string;
  environmentTarget: string;
  deployDisabled: boolean;
  rollbackDisabled: boolean;
  releaseFingerprint: string;
  manifestRef: string;
  signature: string;
  gates: ReleaseGateSummary[];
  blockers: string[];
  affectedSubsystems: string[];
};

export type OpenDecisionGate = {
  decisionId: string;
  title: string;
  status: string;
  resolutionRef: string;
};

export type DriftAlertSummary = {
  alertId: string;
  severity: string;
  environment: string;
  owner: string;
  summary: string;
  acknowledged: boolean;
};

export type IncidentReviewSummary = {
  incidentId: string;
  status: string;
  rollbackTarget: string;
  rcaLinked: boolean;
  correctiveActionDone: boolean;
  closeDisabled: boolean;
  closureGate: string;
};

export type OverrideLedgerEntry = {
  ledgerId: string;
  actor: string;
  timestamp: string;
  fieldPath: string;
  oldValue: string;
  newValue: string;
  policyRef: string;
  reason: string;
  approvalRequired: boolean;
  auditRef: string;
};

export type AdminGovernanceView = {
  tenantContext: string;
  adminRole: string;
  dependencyStatus: string;
  uiTraceId: string;
  traceMetadata: AdminTraceMetadata;
  policies: PolicyVersionSummary[];
  featureFlags: FeatureFlagSummary[];
  marketRules: MarketRuleSummary[];
  changeRequests: ChangeRequestSummary[];
  releaseCandidate: ReleaseCandidateReadiness;
  openDecisions: OpenDecisionGate[];
  driftAlerts: DriftAlertSummary[];
  incidents: IncidentReviewSummary[];
  overrideLedger: OverrideLedgerEntry[];
  events: string[];
  fallbackReason: string;
};

const adminHeaders = {
  Accept: 'application/json',
  'X-Ui-Trace-Id': 'ag-s09-local-trace',
  'X-Tenant-Context': 'ui-preview-tenant',
  'X-Admin-Role': 'release-governance-preview',
};

export async function fetchAdminGovernance(fetchImpl: typeof fetch = fetch): Promise<AdminGovernanceView> {
  const response = await fetchImpl('/api/v1/admin/governance', { headers: adminHeaders });
  if (response.status >= 500) throw new Error('BFF admin governance boundary is temporarily unavailable.');
  return (await response.json()) as AdminGovernanceView;
}
