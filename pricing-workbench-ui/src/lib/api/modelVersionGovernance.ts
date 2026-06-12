export type ModelRegistryRow = {
  modelId: string;
  version: string;
  status: string;
  registeredBy: string;
  registeredAt: string;
  artifacts: Array<{ artifactId: string; checksum: string; locationRef: string }>;
  metadata: string[];
  validationStatus: string;
  validationMessages: string[];
};

export type ModelApprovalRow = {
  modelId: string;
  version: string;
  approvalStatus: string;
  requestedBy: string;
  requestedAt: string;
  approvers: string[];
  criteria: Array<{ criterionId: string; label: string; status: string; evidenceRef: string; configuredValueRef?: string }>;
  decision: string;
  auditEvents: string[];
};

export type ModelLifecycleRow = {
  modelId: string;
  version: string;
  currentStatus: string;
  promotedAt?: string;
  promotedBy?: string;
  suspendedAt?: string;
  suspendedBy?: string;
  deprecatedAt?: string;
  transitionBlockers: string[];
  monitoringConfigRef?: string;
  compatibilityCheckRef?: string;
  replacementModel?: string;
  migrationTimeline?: string;
};

export type CompatibilityRow = {
  modelVersion: string;
  consumerService: string;
  status: string;
  breakingChanges: string[];
  migrationPath: string;
  testResults: string[];
};

export type MonitoringMetric = {
  metricId: string;
  label: string;
  status: string;
  valueLabel: string;
  thresholdRef: string;
  evidenceRef: string;
};

export type MonitoringAlert = {
  alertId: string;
  severity: string;
  status: string;
  notificationChannels: string[];
  escalationOwner: string;
  blocker?: string;
};

export type ModelVersionGovernanceView = {
  tenantContext: string;
  dependencyStatus: string;
  uiTraceId: string;
  fallbackReason: string;
  registry: ModelRegistryRow[];
  approvals: ModelApprovalRow[];
  lifecycle: ModelLifecycleRow[];
  compatibility: CompatibilityRow[];
  monitoring: {
    drift: MonitoringMetric[];
    performance: MonitoringMetric[];
    alerting: MonitoringAlert[];
  };
  actions: {
    registerEnabled: boolean;
    approvalDecisionEnabled: boolean;
    lifecycleTransitionEnabled: boolean;
    compatibilityCheckEnabled: boolean;
  };
  events: string[];
};

const governanceHeaders = {
  Accept: 'application/json',
  'X-Ui-Trace-Id': 'mvg-s26-local-trace',
  'X-Tenant-Context': 'ui-preview-tenant',
};

export async function fetchModelVersionGovernance(fetchImpl: typeof fetch = fetch): Promise<ModelVersionGovernanceView> {
  const response = await fetchImpl('/api/v1/ml-advisory/governance', { headers: governanceHeaders });
  if (response.status >= 500) throw new Error('ML advisory governance boundary is temporarily unavailable.');
  return (await response.json()) as ModelVersionGovernanceView;
}
