export type MarginRedactionEvidence = {
  fieldLabel: string;
  state: string;
  reason: string;
  auditRef: string;
};

export type MarginEvidenceSection = {
  sectionId: string;
  label: string;
  sourceRef: string;
  permissionState: string;
  evidenceRefs: string[];
  redactions: MarginRedactionEvidence[];
};

export type MarginFloorEvidence = {
  quoteOptionId: string;
  decision: string;
  decisionCode: string;
  floorPolicyVersionRef: string;
  thresholdRef: string;
  exceptionRouteRef: string;
  auditRefs: string[];
  displayGuidance: string;
};

export type MarginProfitabilityView = {
  tenantContext: string;
  dependencyStatus: string;
  compensationDetailsVisible: boolean;
  sections: MarginEvidenceSection[];
  floorEvidence: MarginFloorEvidence;
  versionRefs: string[];
  auditRefs: string[];
  replayHash: string;
  uiTraceId: string;
  events: string[];
  fallbackReason: string;
};

export async function fetchMarginProfitability(
  tenantContext: string,
  fetchImpl: typeof fetch = fetch,
): Promise<MarginProfitabilityView> {
  const response = await fetchImpl('/api/v1/margins/profitability', {
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
