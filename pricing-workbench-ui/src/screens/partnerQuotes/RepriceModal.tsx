import type { PartnerRepriceResult } from '../../lib/api/partnerQuotes';
import type { PartnerQuoteDetailView } from './types';

type RepriceModalProps = {
  quote: PartnerQuoteDetailView;
  result: PartnerRepriceResult | null;
  onClose: () => void;
  onRequest: () => void;
};

export function RepriceModal({ quote, result, onClose, onRequest }: RepriceModalProps) {
  return (
    <section className="panel" role="dialog" aria-modal="true" aria-labelledby="reprice-modal-heading">
      <div className="panel-heading-row">
        <div>
          <p className="eyebrow">Reprice guidance</p>
          <h2 id="reprice-modal-heading">Request Reprice</h2>
        </div>
        <button type="button" className="button-secondary" onClick={onClose}>Close</button>
      </div>
      <dl className="status-grid">
        <dt>Current quote</dt><dd><code>{quote.quoteId}</code></dd>
        <dt>Requested by</dt><dd><code>{quote.requestedBy}</code></dd>
        <dt>Partner</dt><dd>{quote.partner}</dd>
        <dt>Status</dt><dd>{quote.status}</dd>
        <dt>Guidance</dt><dd>{quote.actions.reprice.guidance || quote.guidance}</dd>
        <dt>Support handoff route</dt><dd><code>{quote.actions.reprice.supportHandoffRoute}</code></dd>
      </dl>
      <p className="field-help">This action records a partner reprice request through the configured API contract; pricing calculations remain service-owned. The browser does not calculate prices, rates, margins, or SLA values.</p>
      <button type="button" onClick={onRequest} disabled={!quote.actions.reprice.permitted}>Request Reprice</button>
      {!quote.actions.reprice.permitted ? <div className="banner banner--blocked" role="alert">Reprice requires a permitted partner quote action from the configured API response.</div> : null}
      {result ? (
        <div className={result.status === 'ACCEPTED' ? 'banner banner--success' : 'banner banner--blocked'} role={result.status === 'ACCEPTED' ? 'status' : 'alert'}>
          <strong>{result.status}</strong>
          <span>{result.message}</span>
          <span>{result.guidance}</span>
          <span><code>{result.supportHandoffRoute}</code></span>
        </div>
      ) : null}
    </section>
  );
}
