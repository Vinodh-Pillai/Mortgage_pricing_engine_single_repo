export type ExceptionConcessionSection = {
  sectionId: string;
  label: string;
  status: string;
  backendRefs: string[];
  auditRefs: string[];
  summary: string;
};

export type ConcessionRequestView = {
  requestId: string;
  type: string;
  status: string;
  requestorRef: string;
  quoteRef: string;
  impactRef: string;
  createdRef: string;
  assignedApproverRef: string;
  slaRef: string;
  allowedActions: string[];
  auditRefs: string[];
};

export type EligibilityExceptionView = {
  exceptionId: string;
  ruleCodeRef: string;
  quoteRef: string;
  borrowerFactRef: string;
  standardValueRef: string;
  exceptionValueRef: string;
  overrideType: string;
  status: string;
  approverRef: string;
  expirationRef: string;
  renewalWorkflowRef: string;
  auditRefs: string[];
};

export type AuthorityMatrixEntryView = {
  roleRef: string;
  exceptionTypeRef: string;
  limitRef: string;
  escalationPathRef: string;
  governanceConfigRef: string;
  auditRefs: string[];
};

export type PriceGuardAttemptView = {
  attemptId: string;
  quoteRef: string;
  decision: string;
  reasonCodes: string[];
  auditRef: string;
  replayHash: string;
};

export type RiskEventView = {
  eventId: string;
  type: string;
  severity: string;
  quoteRef: string;
  detectedAtRef: string;
  status: string;
  ownerRef: string;
  alertRefs: string[];
  actionRefs: string[];
  auditRefs: string[];
};

export type HistoryReplayExportView = {
  eventId: string;
  type: string;
  status: string;
  actorRef: string;
  replayHash: string;
  exportRef: string;
  retentionClassRef: string;
  legalHoldRef: string;
  auditRefs: string[];
};

export type ManualPriceMutationGuardView = {
  commitDisabled: boolean;
  decision: string;
  reasonCodes: string[];
  escalationPath: string;
  auditRef: string;
  replayHash: string;
};

export type ExceptionConcessionWorkbenchView = {
  tenantContext: string;
  dependencyStatus: string;
  status: string;
  sections: ExceptionConcessionSection[];
  concessionRequests?: ConcessionRequestView[];
  eligibilityExceptions?: EligibilityExceptionView[];
  authorityMatrix?: AuthorityMatrixEntryView[];
  manualPriceMutationGuard: ManualPriceMutationGuardView;
  priceGuardAttempts?: PriceGuardAttemptView[];
  riskEvents?: RiskEventView[];
  historyReplayExports?: HistoryReplayExportView[];
  crossServiceRefs: string[];
  versionRefs: string[];
  auditRefs: string[];
  blockers: string[];
  replayHash: string;
  exportManifestRef: string;
  uiTraceId: string;
  events: string[];
  fallbackReason: string;
};

export async function fetchExceptionConcessionWorkbench(
  tenantContext: string,
  fetchImpl: typeof fetch = fetch,
): Promise<ExceptionConcessionWorkbenchView> {
  const response = await fetchImpl(`/api/v1/exceptions/concessions/workbench?tenantContext=${encodeURIComponent(tenantContext)}`, {
    headers: {
      Accept: 'application/json',
      'X-Tenant-Context': tenantContext,
      'X-Ui-Trace-Id': 'exception-s17-local-trace',
    },
  });

  if (response.status >= 500) {
    throw new Error('Exception concession workbench is temporarily unavailable.');
  }

  return (await response.json()) as ExceptionConcessionWorkbenchView;
}
