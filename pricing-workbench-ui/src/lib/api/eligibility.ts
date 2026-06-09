export type CacheFreshnessView = {
  status: string;
  cacheRef: string;
  indicatorText: string;
};

export type EligibilityDecisionView = {
  decisionId: string;
  decision: 'ELIGIBLE' | 'INELIGIBLE' | 'CONDITIONAL' | string;
  reasonCodes: string[];
  inputFactRefs: string[];
  overlayRefs: string[];
  cacheFreshness: CacheFreshnessView;
  explanationText: string;
  references: string[];
};

export type EligibilityBlockerView = {
  reasonCode: string;
  factRef: string;
  message: string;
};

export type EligibilityModuleView = {
  runId: string;
  quoteOptionId: string;
  status: string;
  decisions: EligibilityDecisionView[];
  blockers: EligibilityBlockerView[];
  requiredNextFacts: string[];
  fallbackReason?: string | null;
  uiTraceId: string;
  events: string[];
};

const traceHeaders = {
  Accept: 'application/json',
  'X-Ui-Trace-Id': 'brw-s04-local-trace',
};

export async function fetchEligibilityModule(
  tenantId: string,
  runId: string,
  fetchImpl: typeof fetch = fetch,
): Promise<EligibilityModuleView> {
  const response = await fetchImpl(`/api/v1/tenants/${encodeURIComponent(tenantId)}/quote-runs/${encodeURIComponent(runId)}/eligibility`, {
    headers: traceHeaders,
  });
  if (response.status >= 500) throw new Error('BFF eligibility explanation boundary is temporarily unavailable.');
  return (await response.json()) as EligibilityModuleView;
}
