import type { PricingWaterfallView } from './quoteRuns';

export type OfferSummary = {
  offerId: string;
  rank: number;
  productLabel?: string | null;
  productFamily?: string | null;
  investor?: string | null;
  rate?: string | number | null;
  payment?: string | number | null;
  apr?: string | number | null;
  confidence?: string | number | null;
  rankScore?: string | number | null;
  lockPeriodDays?: string | number | null;
  eligibilityStatus?: string | null;
  rationaleChips: string[];
  scenarioFlags: string[];
  explanationStatus: 'AVAILABLE' | 'MISSING' | 'BLOCKED' | string;
  commitBlocked?: boolean;
  requiredFacts?: string[];
  sourceScenarioId?: string | null;
  scenarioVersion?: number | null;
  upstreamRefs?: string[];
  lockEligibilityRefs?: string[];
  snapshotRefs?: string[];
  auditIds?: string[];
  explanationSections?: string[];
};

export type OfferComparisonView = {
  runId: string;
  status: string;
  offers: OfferSummary[];
  sortOptions: string[];
  selectedOfferId: string | null;
  commitBlocked: boolean;
  fallbackReason?: string | null;
  requiredFacts?: string[];
  backendRefs?: string[];
  uiTraceId: string;
  events: string[];
};

export type OfferExplanationView = {
  runId: string;
  offerId: string;
  status: 'AVAILABLE' | 'MISSING' | 'BLOCKED' | string;
  rationaleLines: string[];
  scenarioFlags: string[];
  upstreamRefs?: string[];
  snapshotRefs?: string[];
  auditIds?: string[];
  explanationSections?: string[];
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
  scenarioVersion?: number | null;
  lockEligibilityRef?: string | null;
  snapshotRef?: string | null;
  auditIds?: string[];
  auditRef: string | null;
  message: string;
  uiTraceId: string;
  events: string[];
};

export type QuoteDetailPanel = {
  panelId: string;
  label: string;
  status: string;
  fields: string[];
  backendRefs: string[];
  blockers: string[];
};

export type QuoteDetailRedaction = {
  fieldPath: string;
  state: string;
  reason: string;
  auditRef: string;
};

export type QuoteDetailView = {
  tenantContext: string;
  runId: string;
  offerId: string;
  status: string;
  summary: OfferSummary;
  explanation: OfferExplanationView;
  waterfall: PricingWaterfallView;
  panels: QuoteDetailPanel[];
  redactions: QuoteDetailRedaction[];
  complianceFlags: string[];
  auditRefs: string[];
  replayHash: string;
  evidenceHash: string;
  uiTraceId: string;
  events: string[];
  fallbackReason: string;
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

export async function fetchQuoteDetail(
  tenantId: string,
  runId: string,
  offerId: string,
  fetchImpl: typeof fetch = fetch,
): Promise<QuoteDetailView> {
  const response = await fetchImpl(
    `/api/v1/tenants/${encodeURIComponent(tenantId)}/quote-runs/${encodeURIComponent(runId)}/offers/${encodeURIComponent(offerId)}/detail`,
    { headers: { ...traceHeaders, 'X-Ui-Trace-Id': 'qd-s23-local-trace' } },
  );
  if (response.status >= 500) throw new Error('Quote detail evidence boundary is temporarily unavailable.');
  return (await response.json()) as QuoteDetailView;
}

export async function selectOffer(
  tenantId: string,
  runId: string,
  offerId: string,
  sourceScenarioId: string | null | undefined,
  scenarioVersion: number | null | undefined,
  lockEligibilityRefs: string[] | undefined,
  snapshotRefs: string[] | undefined,
  auditIds: string[] | undefined,
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
      body: JSON.stringify({ sourceScenarioId, scenarioVersion, lockEligibilityRefs, snapshotRefs, auditIds }),
    },
  );
  return (await response.json()) as OfferSelectionResult;
}
