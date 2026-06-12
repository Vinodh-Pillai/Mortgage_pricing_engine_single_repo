import { useEffect, useMemo, useState } from 'react';
import { fetchPartnerQuoteDetail, fetchPartnerQuotes, requestPartnerReprice, type PartnerRepriceResult } from '../../lib/api/partnerQuotes';
import { QuoteDetail } from './QuoteDetail';
import { QuoteList } from './QuoteList';
import {
  defaultPartnerQuoteFilters,
  normalizePartnerQuoteDetail,
  normalizePartnerQuoteList,
  type PartnerQuoteDetailView,
  type PartnerQuoteFilters,
  type PartnerQuotesView,
} from './types';

type PartnerQuotesLayoutProps = {
  tenantContext?: string;
  partnerId?: string;
  evidence?: PartnerQuotesView;
  detailEvidence?: PartnerQuoteDetailView;
  fetchImpl?: typeof fetch;
  onEvidenceCapture?: (evidence: { screenId: string; state: string; evidenceTarget: string; refs: string[] }) => void;
};

type ListState =
  | { kind: 'loading' }
  | { kind: 'loaded'; view: PartnerQuotesView }
  | { kind: 'unreachable'; message: string };

type DetailState =
  | { kind: 'idle' }
  | { kind: 'loading' }
  | { kind: 'loaded'; detail: PartnerQuoteDetailView }
  | { kind: 'unreachable'; message: string };

const evidenceTarget = '.local-harness/evidence/PII-24-S21/partner-quotes.json';
const defaultTenantContext = 'ui-preview-tenant';
const defaultPartnerId = 'partner-fixture';

export default function PartnerQuotesLayout({ tenantContext = defaultTenantContext, partnerId = defaultPartnerId, evidence, detailEvidence, fetchImpl, onEvidenceCapture }: PartnerQuotesLayoutProps) {
  const [filters, setFilters] = useState<PartnerQuoteFilters>(defaultPartnerQuoteFilters);
  const [listState, setListState] = useState<ListState>(() => evidence ? { kind: 'loaded', view: normalizePartnerQuoteList(evidence, partnerId) } : { kind: 'loading' });
  const [selectedQuoteId, setSelectedQuoteId] = useState<string | null>(() => evidence?.quotes[0]?.quoteId ?? detailEvidence?.quoteId ?? null);
  const [detailState, setDetailState] = useState<DetailState>(() => detailEvidence ? { kind: 'loaded', detail: normalizePartnerQuoteDetail(detailEvidence, partnerId) } : { kind: 'idle' });
  const [showRepriceModal, setShowRepriceModal] = useState(false);
  const [repriceResult, setRepriceResult] = useState<PartnerRepriceResult | null>(null);

  useEffect(() => {
    if (evidence) {
      const view = normalizePartnerQuoteList(evidence, partnerId);
      setListState({ kind: 'loaded', view });
      setSelectedQuoteId((current) => current ?? view.quotes[0]?.quoteId ?? null);
      return;
    }

    let active = true;
    setListState({ kind: 'loading' });
    const apiStatus = filters.status === 'all' ? '' : filters.status;
    fetchPartnerQuotes(partnerId, apiStatus, fetchImpl)
      .then((view) => {
        if (!active) return;
        const normalized = normalizePartnerQuoteList(view, partnerId);
        setListState({ kind: 'loaded', view: normalized });
        setSelectedQuoteId((current) => current && normalized.quotes.some((quote) => quote.quoteId === current) ? current : normalized.quotes[0]?.quoteId ?? null);
      })
      .catch((error: unknown) => {
        const message = error instanceof Error ? error.message : 'Partner quote list is unavailable.';
        if (active) setListState({ kind: 'unreachable', message });
      });
    return () => {
      active = false;
    };
  }, [evidence, fetchImpl, filters.status, partnerId]);

  useEffect(() => {
    if (detailEvidence) {
      setDetailState({ kind: 'loaded', detail: normalizePartnerQuoteDetail(detailEvidence, partnerId) });
      return;
    }
    if (!selectedQuoteId) {
      setDetailState({ kind: 'idle' });
      return;
    }

    const localView = listState.kind === 'loaded' ? listState.view : null;
    const localQuote = localView?.quotes.find((quote) => quote.quoteId === selectedQuoteId) ?? null;
    if (localQuote) {
      setDetailState({ kind: 'loaded', detail: normalizePartnerQuoteDetail({ ...localQuote, tenantContext, partnerId, actions: { reprice: { visible: true, permitted: true, guidance: localQuote.guidance, supportHandoffRoute: localQuote.supportHandoffRoute } }, uiTraceId: localView?.uiTraceId ?? 'partner-quotes-s21-local-trace' }, partnerId) });
      return;
    }

    let active = true;
    setDetailState({ kind: 'loading' });
    fetchPartnerQuoteDetail(partnerId, selectedQuoteId, fetchImpl)
      .then((detail) => {
        if (active) setDetailState({ kind: 'loaded', detail: normalizePartnerQuoteDetail(detail, partnerId) });
      })
      .catch((error: unknown) => {
        const message = error instanceof Error ? error.message : 'Partner quote detail is unavailable.';
        if (active) setDetailState({ kind: 'unreachable', message });
      });
    return () => {
      active = false;
    };
  }, [detailEvidence, fetchImpl, listState, partnerId, selectedQuoteId, tenantContext]);

  useEffect(() => {
    if (listState.kind !== 'loaded') return;
    onEvidenceCapture?.({ screenId: 'partner-quotes', state: stateForPartnerQuotes(listState.view), evidenceTarget, refs: collectPartnerQuoteRefs(listState.view) });
  }, [listState, onEvidenceCapture]);

  const view = listState.kind === 'loaded' ? listState.view : null;
  const detail = detailState.kind === 'loaded' ? detailState.detail : null;
  const blockedMessage = listState.kind === 'unreachable' ? listState.message : detailState.kind === 'unreachable' ? detailState.message : '';
  const dependencyState = view ? stateForPartnerQuotes(view) : blockedMessage ? 'blocked' : 'load-state';
  const summaryRefs = useMemo(() => view ? collectPartnerQuoteRefs(view).slice(0, 4) : [], [view]);

  async function requestReprice() {
    if (!detail) return;
    const result = await requestPartnerReprice(partnerId, detail.quoteId, fetchImpl);
    setRepriceResult(result);
  }

  return (
    <>
      <section className="hero hero--admin" aria-labelledby="partner-quotes-title">
        <p className="eyebrow">Partner quotes - PII-24-S21</p>
        <h2 id="partner-quotes-title">Partner Quotes</h2>
        <p>Manage partner-submitted quotes with lifecycle, SLA state, lock state, error flag, and reprice request visibility. Pricing logic and SLA calculations remain service-owned.</p>
      </section>

      <section className="panel" aria-labelledby="partner-quotes-heading">
        <div className="panel-heading-row"><div><p className="eyebrow">Tenant context</p><h2 id="partner-quotes-heading">Partner quotes workspace</h2></div></div>
        <dl className="status-grid">
          <dt>Tenant</dt><dd>{tenantContext}</dd>
          <dt>Partner selector</dt><dd>{partnerId}</dd>
          <dt>Status</dt><dd>{view?.dependencyStatus ?? dependencyState}</dd>
          <dt>Support reference</dt><dd><code>{view?.uiTraceId ?? 'partner-quotes-s21-local-trace'}</code></dd>
          <dt>Evidence target</dt><dd><code>{evidenceTarget}</code></dd>
        </dl>
        <div className={dependencyState === 'blocked' ? 'banner banner--blocked' : 'banner banner--info'} role={dependencyState === 'blocked' ? 'alert' : 'status'}>
          <strong>{view?.dependencyStatus ?? 'Loading partner quote read model'}</strong>
          <span>{view?.fallbackReason ?? 'Waiting for partner quote refs.'}</span>
        </div>
        {summaryRefs.length ? <ul className="chip-list" aria-label="Partner quote evidence refs">{summaryRefs.map((ref) => <li key={ref}>{ref}</li>)}</ul> : null}
      </section>

      {listState.kind === 'loading' ? <section className="panel" aria-labelledby="partner-quote-list-heading"><h2 id="partner-quote-list-heading">Partner quote list</h2><p role="status">Loading partner quotes...</p></section> : null}
      {view ? <QuoteList quotes={view.quotes} filters={filters} selectedQuoteId={selectedQuoteId} onFiltersChange={setFilters} onSelectQuote={(quoteId) => { setSelectedQuoteId(quoteId); setShowRepriceModal(false); setRepriceResult(null); }} /> : null}
      <QuoteDetail detail={detail} loading={detailState.kind === 'loading'} blockedMessage={blockedMessage} showRepriceModal={showRepriceModal} repriceResult={repriceResult} onOpenReprice={() => setShowRepriceModal(true)} onCloseReprice={() => setShowRepriceModal(false)} onRequestReprice={() => void requestReprice()} />
    </>
  );
}

export function stateForPartnerQuotes(view: PartnerQuotesView) {
  if ((view.dependencyStatus ?? '').toLowerCase().includes('blocked')) return 'blocked';
  if (view.quotes.length === 0) return 'empty';
  return 'ready';
}

export function collectPartnerQuoteRefs(view: PartnerQuotesView) {
  return view.quotes.flatMap((quote) => [quote.quoteId, quote.createdRef, quote.updatedRef, ...quote.errorFlags, ...quote.lifecycleEvents.map((event) => event.eventId)]);
}

export { evidenceTarget as partnerQuotesEvidenceTarget };
