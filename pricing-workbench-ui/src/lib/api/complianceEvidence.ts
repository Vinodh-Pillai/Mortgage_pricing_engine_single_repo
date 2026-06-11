export type ComplianceEvidenceArtifact = {
  artifactId: string;
  path: string;
  artifactType: string;
  owner: string;
  retentionClass: string;
  relatedModule: string;
  version: string;
  hash: string;
  traceId: string;
  policyVersion: string;
  policyDigest: string;
  jurisdictionCode: string;
  continuityStatus: string;
  moduleLinks: string[];
  progressionBlocked: boolean;
  blockers: string[];
};

export type ComplianceDecisionRationale = {
  decisionId: string;
  reasonCode: string;
  humanText: string;
  jurisdictionCode: string;
  reasonTiers: string[];
  exportBlocked: boolean;
  disclosureArtifactRef: string;
};

export type ComplianceAdvisoryReview = {
  reviewId: string;
  reviewType: string;
  subjectRef: string;
  status: string;
  reasonCodes: string[];
  auditSnapshotRefs: string[];
  regulatoryApprovalState: string;
  exportRefs: string[];
  blockedByConfiguration: boolean;
  configurationGaps: string[];
};

export type FairLendingMonitoringDrilldown = {
  drilldownId: string;
  dimensions: string[];
  redacted: boolean;
  redactionState: string;
  evidenceRefs: string[];
  blockers: string[];
};

export type PrivacyRequestSummary = {
  requestId: string;
  borrowerRef: string;
  requestedScope: string;
  identityStatus: string;
  slaState: string;
  consentAuditRef: string;
  blockers: string[];
};

export type SecurityEventSummary = {
  eventId: string;
  category: string;
  severity: string;
  owner: string;
  logRecordId: string;
  correlationId: string;
  acknowledged: boolean;
  blockers: string[];
};

export type ComplianceAlertSummary = {
  alertId: string;
  severity: string;
  alertClass: string;
  triggerType: string;
  routeTarget: string;
  acknowledged: boolean;
  blockers: string[];
};

export type RetentionControlSummary = {
  ruleId: string;
  retentionClass: string;
  retentionWindow: string;
  legalHoldActive: boolean;
  deletionGateReason: string;
  backupEvidence: string;
};

export type ComplianceEvidenceRegistryView = {
  tenantContext: string;
  dependencyStatus: string;
  artifacts: ComplianceEvidenceArtifact[];
  decisions: ComplianceDecisionRationale[];
  advisoryReviews: ComplianceAdvisoryReview[];
  fairLendingMonitoring: FairLendingMonitoringDrilldown[];
  privacyRequests: PrivacyRequestSummary[];
  securityEvents: SecurityEventSummary[];
  alerts: ComplianceAlertSummary[];
  retentionControls: RetentionControlSummary[];
  configurationGaps: string[];
  uiTraceId: string;
  events: string[];
  fallbackReason: string;
};

const complianceHeaders = {
  Accept: 'application/json',
  'X-Ui-Trace-Id': 'sec-s07-local-trace',
  'X-Tenant-Context': 'ui-preview-tenant',
};

export async function fetchComplianceEvidenceRegistry(
  fetchImpl: typeof fetch = fetch,
): Promise<ComplianceEvidenceRegistryView> {
  const response = await fetchImpl('/api/v1/compliance/evidence', { headers: complianceHeaders });
  if (response.status >= 500) throw new Error('BFF compliance evidence boundary is temporarily unavailable.');
  return (await response.json()) as ComplianceEvidenceRegistryView;
}
