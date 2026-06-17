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

type RawRecord = Record<string, unknown>;

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
  return normalizeOfferComparison(await response.json(), runId);
}

function normalizeOfferComparison(raw: unknown, requestedRunId: string): OfferComparisonView {
  const value = objectValue(raw);
  const rawOffers = arrayValue(value.offers)
    ?? arrayValue(value.options)
    ?? arrayValue(value.rankedOptions)
    ?? arrayValue(value.rows)
    ?? [];
  const offers = rawOffers.map((offer, index) => normalizeOffer(offer, index)).filter((offer): offer is OfferSummary => Boolean(offer));
  const sortOptions = normalizeSortOptions(arrayValue(value.sortOptions), arrayValue(value.columns));
  const runId = stringValue(value.runId) || stringValue(value.quoteId) || requestedRunId;

  return {
    runId,
    status: stringValue(value.status) || (offers.length > 0 ? 'QUOTE_SERVICE_EVIDENCE_VISIBLE' : 'NO_OFFERS'),
    offers,
    sortOptions,
    selectedOfferId: stringValue(value.selectedOfferId) || null,
    commitBlocked: booleanValue(value.commitBlocked) ?? offers.length === 0,
    fallbackReason: nullableString(value.fallbackReason) ?? (offers.length === 0 ? 'Offer comparison response did not include offers, options, rankedOptions, or rows.' : null),
    requiredFacts: stringArray(value.requiredFacts),
    backendRefs: stringArray(value.backendRefs),
    uiTraceId: stringValue(value.uiTraceId) || traceHeaders['X-Ui-Trace-Id'],
    events: stringArray(value.events),
  };
}

function normalizeOffer(raw: unknown, index: number): OfferSummary | null {
  const value = objectValue(raw);
  const offerId = stringValue(value.offerId) || stringValue(value.optionId) || stringValue(value.id);
  if (!offerId) return null;
  const rank = numberValue(value.rank) ?? index + 1;

  return {
    offerId,
    rank,
    productLabel: nullableString(value.productLabel) ?? nullableString(value.product) ?? nullableString(value.productId),
    productFamily: nullableString(value.productFamily),
    investor: nullableString(value.investor) ?? nullableString(value.investorLabel) ?? nullableString(value.investorId),
    rate: nullablePrimitive(value.rate) ?? nullablePrimitive(value.noteRate),
    payment: nullablePrimitive(value.payment),
    apr: nullablePrimitive(value.apr),
    confidence: nullablePrimitive(value.confidence) ?? nullablePrimitive(value.eligibilityStatus),
    rankScore: nullablePrimitive(value.rankScore) ?? nullablePrimitive(value.score),
    lockPeriodDays: nullablePrimitive(value.lockPeriodDays) ?? nullablePrimitive(value.lockDays),
    eligibilityStatus: nullableString(value.eligibilityStatus),
    rationaleChips: stringArray(value.rationaleChips).concat(stringArray(value.rankReasons), stringArray(value.warnings)),
    scenarioFlags: stringArray(value.scenarioFlags),
    explanationStatus: stringValue(value.explanationStatus) || 'MISSING',
    commitBlocked: booleanValue(value.commitBlocked),
    requiredFacts: stringArray(value.requiredFacts),
    sourceScenarioId: nullableString(value.sourceScenarioId) ?? nullableString(value.scenarioId),
    scenarioVersion: numberValue(value.scenarioVersion),
    upstreamRefs: stringArray(value.upstreamRefs),
    lockEligibilityRefs: stringArray(value.lockEligibilityRefs),
    snapshotRefs: stringArray(value.snapshotRefs),
    auditIds: stringArray(value.auditIds),
    explanationSections: stringArray(value.explanationSections),
  };
}

function normalizeSortOptions(rawSortOptions: unknown[] | null, rawColumns: unknown[] | null): string[] {
  const fromSortOptions = stringArray(rawSortOptions);
  if (fromSortOptions.length > 0) return fromSortOptions;
  const fromColumns = (rawColumns ?? [])
    .map((column) => objectValue(column))
    .map((column) => stringValue(column.field) || stringValue(column.id))
    .filter(Boolean);
  const supported = fromColumns
    .map((field) => field === 'noteRate' ? 'apr' : field === 'score' ? 'confidence' : field)
    .filter((field) => ['rank', 'payment', 'apr', 'confidence'].includes(field));
  return supported.length > 0 ? Array.from(new Set(supported)) : ['rank', 'confidence', 'payment'];
}

function objectValue(value: unknown): RawRecord {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as RawRecord : {};
}

function arrayValue(value: unknown): unknown[] | null {
  return Array.isArray(value) ? value : null;
}

function stringArray(value: unknown): string[] {
  if (!Array.isArray(value)) return [];
  return value.map((item) => stringValue(item)).filter(Boolean);
}

function stringValue(value: unknown): string {
  if (typeof value === 'string') return value;
  if (typeof value === 'number' || typeof value === 'boolean') return String(value);
  return '';
}

function nullableString(value: unknown): string | null {
  const normalized = stringValue(value);
  return normalized || null;
}

function nullablePrimitive(value: unknown): string | number | null {
  if (typeof value === 'number' && Number.isFinite(value)) return value;
  return nullableString(value);
}

function numberValue(value: unknown): number | null {
  if (typeof value === 'number' && Number.isFinite(value)) return value;
  if (typeof value !== 'string' || !value.trim()) return null;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function booleanValue(value: unknown): boolean | undefined {
  return typeof value === 'boolean' ? value : undefined;
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
