import { ChipList } from '../../components/ChipList';
import type { OfferSummary } from '../../lib/api/offers';
import { valueText } from './offerComparison';

export function OfferCard({ offer, selected, compared, onInspect, onSelect, onCompareToggle }: { offer: OfferSummary; selected: boolean; compared: boolean; onInspect: (offer: OfferSummary) => void; onSelect: (offer: OfferSummary) => void; onCompareToggle: (offerId: string) => void }) {
  return (
    <article className={selected ? 'offer-card offer-card--selected' : 'offer-card'} aria-label={`Offer ${offer.offerId} rank ${offer.rank}`}>
      <div className="panel-heading-row">
        <div>
          <p className="eyebrow">Rank #{offer.rank}</p>
          <h3>{offer.productLabel ?? offer.offerId}</h3>
        </div>
        <span>{valueText(offer.confidence)} confidence</span>
      </div>
      <dl className="status-grid">
        <dt>Rate</dt><dd>{valueText(offer.rate)}</dd>
        <dt>APR</dt><dd>{valueText(offer.apr)}</dd>
        <dt>Payment</dt><dd>{valueText(offer.payment)}</dd>
        <dt>Rank score</dt><dd>{valueText(offer.rankScore)}</dd>
        <dt>Investor</dt><dd>{valueText(offer.investor)}</dd>
        <dt>Eligibility</dt><dd>{valueText(offer.eligibilityStatus)}</dd>
      </dl>
      <ChipList label={`${offer.offerId} rationale`} values={offer.rationaleChips} />
      <ChipList label={`${offer.offerId} flags`} values={offer.scenarioFlags} />
      <div className="quick-quote-state">
        <button type="button" aria-pressed={selected} onClick={() => onSelect(offer)}>Select offer</button>
        <button type="button" aria-pressed={compared} onClick={() => onCompareToggle(offer.offerId)}>Compare</button>
        <button type="button" onClick={() => onInspect(offer)}>Preview explanation</button>
      </div>
    </article>
  );
}
