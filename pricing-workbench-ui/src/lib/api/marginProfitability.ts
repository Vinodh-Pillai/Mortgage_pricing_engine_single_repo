export type MarginRedactionEvidence = {
  fieldLabel: string;
  state: string;
  reason: string;
  auditRef: string;
};

export type MarginPermissionState = 'VISIBLE' | 'REDACTED' | 'RESTRICTED' | string;

export type MarginFloorDecision = 'PASS' | 'FAIL' | 'WARN' | string;

export type MarginApprovalStatus = 'DRAFT' | 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED' | string;

export type MarginEvidenceSection = {
  sectionId: string;
  label: string;
  sourceRef: string;
  permissionState: MarginPermissionState;
  displayValue?: string;
  requestAccessAllowed?: boolean;
  evidenceRefs: string[];
  redactions: MarginRedactionEvidence[];
};

export type MarginFloorEvidence = {
  quoteOptionId: string;
  decision: MarginFloorDecision;
  decisionCode: string;
  floorPolicyVersionRef: string;
  thresholdRef: string;
  exceptionRouteRef: string;
  auditRefs: string[];
  displayGuidance: string;
  thresholdCalculationRef?: string;
  exceptionWorkflowRef?: string;
};

export type MarginApprovalStep = {
  role: string;
  actorRef: string;
  status: string;
  timestampRef: string;
  auditRef: string;
};

export type MarginApprovalAction = 'REQUEST_APPROVAL' | 'APPROVE' | 'REJECT' | string;

export type MarginApprovalView = {
  currentVersionRef: string;
  status: MarginApprovalStatus;
  chain: MarginApprovalStep[];
  allowedActions: MarginApprovalAction[];
  snapshotRefs: string[];
  auditRefs: string[];
};

export type MarginProfitabilityView = {
  tenantContext: string;
  dependencyStatus: string;
  status?: string;
  compensationDetailsVisible: boolean;
  sections: MarginEvidenceSection[];
  floorEvidence: MarginFloorEvidence | MarginFloorEvidence[];
  approvalStatus?: MarginApprovalView;
  versionRefs: string[];
  auditRefs: string[];
  replayHash: string;
  uiTraceId: string;
  correlationRefs?: string[];
  events: string[];
  fallbackReason: string;
};

export async function fetchMarginProfitability(
  tenantContext: string,
  fetchImpl: typeof fetch = fetch,
): Promise<MarginProfitabilityView> {
  const response = await fetchImpl(`/api/v1/margins/profitability?tenantContext=${encodeURIComponent(tenantContext)}`, {
    headers: {
      Accept: 'application/json',
      'X-Tenant-Context': tenantContext,
      'X-Ui-Trace-Id': 'margin-s16-local-trace',
    },
  });

  if (response.status >= 500) {
    throw new Error('Margin profitability evidence is temporarily unavailable.');
  }

  return (await response.json()) as MarginProfitabilityView;
}
