import type { PartnerRepriceResult } from '../../lib/api/partnerQuotes';
import { RepriceModal } from './RepriceModal';
import type { PartnerQuoteDetailView } from './types';

type QuoteDetailProps = {
  detail: PartnerQuoteDetailView | null;
  loading: boolean;
  blockedMessage: string;
  showRepriceModal: boolean;
  repriceResult: PartnerRepriceResult | null;
  onOpenReprice: () => void;
  onCloseReprice: () => void;
  onRequestReprice: () => void;
};

export function QuoteDetail({ detail, loading, blockedMessage, showRepriceModal, repriceResult, onOpenReprice, onCloseReprice, onRequestReprice }: QuoteDetailProps) {
  if (loading) return <section className="panel" aria-labelledby="partner-quote-detail-heading"><h2 id="partner-quote-detail-heading">Partner Quote Detail</h2><p role="status">Loading partner quote detail...</p></section>;
  if (blockedMessage) return <section className="panel" aria-labelledby="partner-quote-detail-heading"><h2 id="partner-quote-detail-heading">Partner Quote Detail</h2><div className="banner banner--blocked" role="alert">{blockedMessage}</div></section>;
  if (!detail) return <section className="panel" aria-labelledby="partner-quote-detail-heading"><h2 id="partner-quote-detail-heading">Partner Quote Detail</h2><p>Select a quote row to inspect lifecycle events, SLA refs, lock state, and reprice guidance.</p></section>;

  return (
    <aside className="panel" aria-labelledby="partner-quote-detail-heading">
      <div className="panel-heading-row">
        <div>
          <p className="eyebrow">Side panel</p>
          <h2 id="partner-quote-detail-heading">Partner Quote Detail</h2>
        </div>
        <button type="button" onClick={onOpenReprice}>Reprice</button>
      </div>
      <dl className="status-grid">
        <dt>Quote ID</dt><dd><code>{detail.quoteId}</code></dd>
        <dt>Partner</dt><dd>{detail.partner}</dd>
        <dt>Borrower</dt><dd>{detail.borrowerLabel}</dd>
        <dt>Status</dt><dd>{detail.status}</dd>
        <dt>SLA</dt><dd>{detail.slaState}</dd>
        <dt>Lock</dt><dd>{detail.lockState}</dd>
      </dl>
      <section aria-labelledby="sla-tracking-heading">
        <h3 id="sla-tracking-heading">SLA tracking refs</h3>
        <dl className="status-grid">
          <dt>Target</dt><dd><code>{detail.slaTargetRef}</code></dd>
          <dt>Elapsed</dt><dd><code>{detail.slaElapsedRef}</code></dd>
          <dt>Remaining</dt><dd><code>{detail.slaRemainingRef}</code></dd>
          <dt>Breach prediction</dt><dd><code>{detail.breachPredictionRef}</code></dd>
        </dl>
      </section>
      <section aria-labelledby="error-details-heading">
        <h3 id="error-details-heading">Error details</h3>
        {detail.errorDetails.length === 0 ? <p role="status">No error flags supplied for this quote.</p> : <ul>{detail.errorDetails.map((error) => <li key={error}><code>{error}</code></li>)}</ul>}
      </section>
      <section aria-labelledby="lifecycle-timeline-heading">
        <h3 id="lifecycle-timeline-heading">Lifecycle events</h3>
        <div className="quote-table" role="table" aria-label="Partner quote lifecycle timeline">
          <div role="row" className="quote-table__row quote-table__row--head"><span role="columnheader">Timestamp</span><span role="columnheader">Event type</span><span role="columnheader">Summary</span></div>
          {detail.lifecycleEvents.map((event) => <div key={event.eventId} role="row" className="quote-table__row"><span role="cell"><code>{event.timestampRef}</code></span><span role="cell">{event.eventType}</span><span role="cell">{event.summary}</span></div>)}
        </div>
      </section>
      {showRepriceModal ? <RepriceModal quote={detail} result={repriceResult} onClose={onCloseReprice} onRequest={onRequestReprice} /> : null}
    </aside>
  );
}
