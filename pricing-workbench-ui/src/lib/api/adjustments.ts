export type AdjustmentEvidenceRow = {
  adjustmentId: string;
  label: string;
  category: string;
  status: string;
  factRefs: string[];
  sourceRef: string;
  sourceVersionRef: string;
  summary: string;
  compensationHooks: string[];
  conflictIds: string[];
};

export type AdjustmentConflictView = {
  conflictId: string;
  severity: string;
  reasonCode: string;
  reason: string;
  resolutionOwner: string;
  affectedAdjustmentIds: string[];
};

export type AdjustmentBlockedState = {
  reasonCode: string;
  message: string;
  sourceRef: string;
  resolutionOwner: string;
};

export type AdjustmentSummaryCard = {
  category: string;
  summary: string;
  evidenceRefs: string[];
};

export type AdjustmentEvidenceView = {
  tenantContext: string;
  dependencyStatus: string;
  status: string;
  adjustments: AdjustmentEvidenceRow[];
  conflicts: AdjustmentConflictView[];
  blockers: AdjustmentBlockedState[];
  summaries: AdjustmentSummaryCard[];
  versionRefs: string[];
  auditRefs: string[];
  replayHash: string;
  uiTraceId: string;
  events: string[];
  fallbackReason: string;
};

export async function fetchAdjustmentEvidence(
  tenantContext: string,
  fetchImpl: typeof fetch = fetch,
): Promise<AdjustmentEvidenceView> {
  const response = await fetchImpl('/api/v1/adjustments/evidence', {
    headers: {
      Accept: 'application/json',
      'X-Tenant-Context': tenantContext,
      'X-Ui-Trace-Id': 'adjustment-s17-local-trace',
    },
  });

  if (response.status >= 500) {
    throw new Error('Adjustment evidence is temporarily unavailable.');
  }

  return (await response.json()) as AdjustmentEvidenceView;
}
