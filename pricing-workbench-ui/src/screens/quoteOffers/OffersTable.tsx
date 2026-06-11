import { ChipList } from '../../components/ChipList';
import type { OfferSummary } from '../../lib/api/offers';
import type { KeyboardEvent } from 'react';
import type { OfferSortField } from './offerComparison';
import { valueText } from './offerComparison';

type OffersTableProps = {
  offers: OfferSummary[];
  selectedOfferId: string | null;
  compareOfferIds: string[];
  onInspect: (offer: OfferSummary) => void;
  onSelect: (offer: OfferSummary) => void;
  onCompareToggle: (offerId: string) => void;
  onSortField: (field: OfferSortField, additive: boolean) => void;
};

const boundedRenderCount = 100;

export function OffersTable({ offers, selectedOfferId, compareOfferIds, onInspect, onSelect, onCompareToggle, onSortField }: OffersTableProps) {
  const visible = offers.slice(0, boundedRenderCount);
  return (
    <div className="quote-table" role="table" aria-label="Ranked quote offers" aria-rowcount={offers.length}>
      <div role="row" className="quote-table__row quote-table__row--head">
        <SortableHeader label="Rank" field="rank" onSortField={onSortField} />
        <span role="columnheader">Product</span>
        <SortableHeader label="Rate" field="rate" onSortField={onSortField} />
        <SortableHeader label="APR" field="apr" onSortField={onSortField} />
        <SortableHeader label="Payment" field="payment" onSortField={onSortField} />
        <SortableHeader label="Confidence" field="confidence" onSortField={onSortField} />
        <SortableHeader label="Rank score" field="rankScore" onSortField={onSortField} />
        <span role="columnheader">Rationale</span>
        <span role="columnheader">Flags</span>
        <span role="columnheader">Actions</span>
      </div>
      {visible.map((offer) => {
        const selected = selectedOfferId === offer.offerId;
        return (
          <div key={offer.offerId} role="row" className={selected ? 'quote-table__row quote-table__row--selected' : 'quote-table__row'} aria-selected={selected} tabIndex={0} onKeyDown={(event) => handleRowKey(event, offer, onSelect, onCompareToggle)}>
            <span role="cell">#{offer.rank}</span>
            <span role="cell">{offer.productLabel ?? offer.offerId}</span>
            <span role="cell">{valueText(offer.rate)}</span>
            <span role="cell">{valueText(offer.apr)}</span>
            <span role="cell">{valueText(offer.payment)}</span>
            <span role="cell">{valueText(offer.confidence)}</span>
            <span role="cell">{valueText(offer.rankScore)}</span>
            <span role="cell"><ChipList label={`${offer.offerId} rationale`} values={offer.rationaleChips} /></span>
            <span role="cell"><ChipList label={`${offer.offerId} flags`} values={offer.scenarioFlags} /></span>
            <span role="cell" className="quick-quote-state">
              <label><input type="radio" name="selected-offer" checked={selected} onChange={() => onSelect(offer)} /> Select</label>
              <label><input type="checkbox" checked={compareOfferIds.includes(offer.offerId)} onChange={() => onCompareToggle(offer.offerId)} /> Compare</label>
              <button type="button" onMouseEnter={() => onInspect(offer)} onFocus={() => onInspect(offer)} onClick={() => onInspect(offer)}>Preview</button>
            </span>
          </div>
        );
      })}
      {offers.length > boundedRenderCount ? <p role="status">Showing first {boundedRenderCount} offers from a {offers.length} offer result set.</p> : null}
    </div>
  );
}

function SortableHeader({ label, field, onSortField }: { label: string; field: OfferSortField; onSortField: (field: OfferSortField, additive: boolean) => void }) {
  return <button type="button" role="columnheader" onClick={(event) => onSortField(field, event.shiftKey)}>{label}</button>;
}

function handleRowKey(event: KeyboardEvent, offer: OfferSummary, onSelect: (offer: OfferSummary) => void, onCompareToggle: (offerId: string) => void) {
  if (event.key === 'Enter') {
    event.preventDefault();
    onSelect(offer);
  }
  if (event.key === ' ') {
    event.preventDefault();
    onCompareToggle(offer.offerId);
  }
}
