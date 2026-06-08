export type OfferSummary = {
  offerId: string;
  rank: number;
  productLabel?: string | null;
  payment?: string | number | null;
  apr?: string | number | null;
  confidence?: string | number | null;
  rationaleChips: string[];
  scenarioFlags: string[];
  explanationStatus: 'AVAILABLE' | 'MISSING' | 'BLOCKED' | string;
  sourceScenarioId?: string | null;
};

export type OfferComparisonView = {
  runId: string;
  status: string;
  offers: OfferSummary[];
  sortOptions: string[];
  selectedOfferId: string | null;
  commitBlocked: boolean;
  fallbackReason?: string | null;
  uiTraceId: string;
  events: string[];
};

export type OfferExplanationView = {
  runId: string;
  offerId: string;
  status: 'AVAILABLE' | 'MISSING' | 'BLOCKED' | string;
  rationaleLines: string[];
  scenarioFlags: string[];
  commitBlocked: boolean;
  message: string;
  uiTraceId: string;
};

export type OfferSelectionResult = {
  runId: string;
  selectedOfferId: string | null;
  status: 'SELECTED' | 'BLOCKED' | string;
  nextRoute: string | null;
  sourceScenarioId: string | null;
  auditRef: string | null;
  message: string;
  uiTraceId: string;
  events: string[];
};

const traceHeaders = {
  Accept: 'application/json',
  'X-Ui-Trace-Id': 'brw-s02-local-trace',
};

export async function fetchOfferComparison(
  tenantId: string,
  runId: string,
  fetchImpl: typeof fetch = fetch,
): Promise<OfferComparisonView> {
  const response = await fetchImpl(`/api/v1/tenants/${encodeURIComponent(tenantId)}/quote-runs/${encodeURIComponent(runId)}/offers`, {
    headers: traceHeaders,
  });
  if (response.status >= 500) throw new Error('BFF offer comparison boundary is temporarily unavailable.');
  return (await response.json()) as OfferComparisonView;
}

export async function fetchOfferExplanation(
  tenantId: string,
  runId: string,
  offerId: string,
  fetchImpl: typeof fetch = fetch,
): Promise<OfferExplanationView> {
  const response = await fetchImpl(
    `/api/v1/tenants/${encodeURIComponent(tenantId)}/quote-runs/${encodeURIComponent(runId)}/offers/${encodeURIComponent(offerId)}/explain`,
    { headers: traceHeaders },
  );
  if (response.status >= 500) throw new Error('BFF explainability boundary is temporarily unavailable.');
  return (await response.json()) as OfferExplanationView;
}

export async function selectOffer(
  tenantId: string,
  runId: string,
  offerId: string,
  sourceScenarioId: string | null | undefined,
  fetchImpl: typeof fetch = fetch,
): Promise<OfferSelectionResult> {
  const response = await fetchImpl(
    `/api/v1/tenants/${encodeURIComponent(tenantId)}/quote-runs/${encodeURIComponent(runId)}/offers/${encodeURIComponent(offerId)}/select`,
    {
      method: 'POST',
      headers: {
        ...traceHeaders,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ sourceScenarioId }),
    },
  );
  return (await response.json()) as OfferSelectionResult;
}
