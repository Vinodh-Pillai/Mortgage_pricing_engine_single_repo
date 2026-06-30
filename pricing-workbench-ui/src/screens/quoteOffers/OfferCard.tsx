import { ChipList } from '../../components/ChipList';
import type { OfferSummary } from '../../lib/api/offers';
import { offerDisplayName, valueText, visibleOfferEvidenceValues } from './offerComparison';

export function OfferCard({ offer, selected, compared, onInspect, onSelect, onCompareToggle }: { offer: OfferSummary; selected: boolean; compared: boolean; onInspect: (offer: OfferSummary) => void; onSelect: (offer: OfferSummary) => void; onCompareToggle: (offerId: string) => void }) {
  const displayName = offerDisplayName(offer);
  return (
    <article className={selected ? 'offer-card offer-card--selected' : 'offer-card'} aria-label={`Offer ${displayName}`}>
      <div className="panel-heading-row">
        <div>
          <p className="eyebrow">Offer option</p>
          <h3>{displayName}</h3>
        </div>
        <span>{valueText(offer.eligibilityStatus)}</span>
      </div>
      <dl className="status-grid">
        <dt>Rate</dt><dd>{valueText(offer.rate)}</dd>
        <dt>Price</dt><dd>{valueText(offer.price)}</dd>
        <dt>APR</dt><dd>{valueText(offer.apr)}</dd>
        <dt>Payment</dt><dd>{valueText(offer.payment)}</dd>
        <dt>Lock</dt><dd>{valueText(offer.lockPeriodDays)}</dd>
        <dt>Investor</dt><dd>{valueText(offer.investor)}</dd>
        <dt>Eligibility</dt><dd>{valueText(offer.eligibilityStatus)}</dd>
      </dl>
      <ChipList label={`${displayName} rationale`} values={visibleOfferEvidenceValues(offer.rationaleChips)} />
      <ChipList label={`${displayName} flags`} values={visibleOfferEvidenceValues(offer.scenarioFlags)} />
      <div className="quick-quote-state">
        <button type="button" aria-label={`Select offer ${displayName}`} aria-pressed={selected} onClick={() => onSelect(offer)}>Select offer</button>
        <button type="button" aria-label={`${compared ? 'Remove offer from comparison' : 'Add offer to comparison'} ${displayName}`} aria-pressed={compared} onClick={() => onCompareToggle(offer.offerId)}>{compared ? 'Remove from comparison' : 'Add to comparison'}</button>
        <button type="button" aria-label={`Inspect explanation for offer ${displayName}`} onClick={() => onInspect(offer)}>Inspect explanation</button>
      </div>
    </article>
  );
}
