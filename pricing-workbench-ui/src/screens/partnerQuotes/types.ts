import type { PartnerQuoteDetail, PartnerQuoteListView, PartnerQuoteSummary } from '../../lib/api/partnerQuotes';

export const quoteStatuses = ['SUBMITTED', 'PRICING', 'PRICED', 'LOCKED', 'COMMITTED', 'FUNDED', 'REJECTED', 'WITHDRAWN'] as const;
export const slaStates = ['ON_TRACK', 'AT_RISK', 'BREACHED'] as const;
export const lockStates = ['UNLOCKED', 'LOCKED', 'EXPIRED', 'RELOCKED', 'FLOAT_DOWN'] as const;
export const errorFlags = ['VALIDATION_FAILED', 'PRICING_FAILED', 'LOCK_FAILED', 'COMPLIANCE_BLOCKED', 'DATA_QUALITY'] as const;

export type PartnerQuoteLifecycleEvent = {
  eventId: string;
  eventType: string;
  timestampRef: string;
  summary: string;
};

export type PartnerQuoteRow = PartnerQuoteSummary & {
  partner: string;
  createdRef: string;
  updatedRef: string;
  requestedBy: string;
  guidance: string;
  errorDetails: string[];
  lifecycleEvents: PartnerQuoteLifecycleEvent[];
  slaTargetRef: string;
  slaElapsedRef: string;
  slaRemainingRef: string;
  breachPredictionRef: string;
  supportHandoffRoute: string;
};

export type PartnerQuotesView = Omit<PartnerQuoteListView, 'quotes'> & {
  dependencyStatus?: string;
  fallbackReason?: string;
  quotes: PartnerQuoteRow[];
};

export type PartnerQuoteDetailView = Omit<PartnerQuoteDetail, 'lifecycleEvents'> & PartnerQuoteRow;

type RawPartnerQuote = PartnerQuoteSummary & {
  partner?: string;
  createdRef?: string;
  updatedRef?: string;
  requestedBy?: string;
  guidance?: string;
  errorDetails?: string[];
  lifecycleEvents?: Array<PartnerQuoteLifecycleEvent | string>;
  slaTargetRef?: string;
  slaElapsedRef?: string;
  slaRemainingRef?: string;
  breachPredictionRef?: string;
  supportHandoffRoute?: string;
};

export type PartnerQuoteFilters = {
  partner: string;
  status: string;
  slaState: string;
  lockState: string;
  dateRange: string;
  sort: 'created' | 'updated' | 'sla' | 'borrower';
};

export const defaultPartnerQuoteFilters: PartnerQuoteFilters = {
  partner: 'all',
  status: 'all',
  slaState: 'all',
  lockState: 'all',
  dateRange: 'all',
  sort: 'created',
};

export function normalizePartnerQuoteList(view: PartnerQuoteListView | PartnerQuotesView, partnerFallback = 'partner-fixture'): PartnerQuotesView {
  return {
    ...view,
    dependencyStatus: (view as PartnerQuotesView).dependencyStatus ?? 'PARTNER_QUOTES_READ_MODEL_SUPPLIED',
    fallbackReason: (view as PartnerQuotesView).fallbackReason ?? 'UI renders partner quote refs from backend-shaped data or deterministic fixtures only; no pricing or SLA calculation is performed in the browser.',
    quotes: view.quotes.map((quote) => normalizePartnerQuoteRow(quote, partnerFallback)),
  };
}

export function normalizePartnerQuoteDetail(detail: PartnerQuoteDetail | PartnerQuoteDetailView, partnerFallback = 'partner-fixture'): PartnerQuoteDetailView {
  const row = normalizePartnerQuoteRow(detail, partnerFallback);
  return {
    ...detail,
    ...row,
    tenantContext: detail.tenantContext,
    partnerId: detail.partnerId,
    actions: detail.actions,
    uiTraceId: detail.uiTraceId,
  };
}

export function normalizePartnerQuoteRow(quote: RawPartnerQuote, partnerFallback = 'partner-fixture'): PartnerQuoteRow {
  const extended = quote;
  const lifecycleEvents = normalizeLifecycleEvents(extended.lifecycleEvents ?? []);
  return {
    ...quote,
    partner: extended.partner ?? partnerFallback,
    createdRef: extended.createdRef ?? `created-ref:${quote.quoteId}`,
    updatedRef: extended.updatedRef ?? `updated-ref:${quote.quoteId}`,
    requestedBy: extended.requestedBy ?? 'requested-by-ref:not-supplied',
    guidance: extended.guidance ?? 'Reprice requires an explicit partner quote action contract; the UI does not calculate price changes.',
    errorDetails: extended.errorDetails ?? quote.errorFlags.map((flag) => `error-detail-ref:${flag}`),
    lifecycleEvents: lifecycleEvents.length ? lifecycleEvents : [{ eventId: `${quote.quoteId}-status`, eventType: quote.status, timestampRef: 'timestamp-ref:not-supplied', summary: 'Lifecycle event summary is supplied by partner quote detail when available.' }],
    slaTargetRef: extended.slaTargetRef ?? 'sla-target-ref:not-supplied',
    slaElapsedRef: extended.slaElapsedRef ?? 'sla-elapsed-ref:not-supplied',
    slaRemainingRef: extended.slaRemainingRef ?? 'sla-remaining-ref:not-supplied',
    breachPredictionRef: extended.breachPredictionRef ?? 'sla-breach-prediction-ref:not-supplied',
    supportHandoffRoute: extended.supportHandoffRoute ?? '/partners/support/reprice',
  };
}

function normalizeLifecycleEvents(events: Array<PartnerQuoteLifecycleEvent | string>) {
  return events.map((event, index) => typeof event === 'string'
    ? { eventId: `event-${index + 1}`, eventType: event, timestampRef: 'timestamp-ref:not-supplied', summary: event }
    : event);
}
