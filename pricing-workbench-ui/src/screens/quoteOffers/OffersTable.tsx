import type { OfferSummary } from '../../lib/api/offers';
import type { CSSProperties, KeyboardEvent } from 'react';
import type { OfferSortField } from './offerComparison';
import { offerDisplayName, valueText, visibleOfferEvidenceValues } from './offerComparison';
import { ChipList } from '../../components/ChipList';

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

const tableShellStyle: CSSProperties = {
  maxWidth: '100%',
  overflowX: 'auto',
  overscrollBehaviorInline: 'contain',
};

const tableGridStyle: CSSProperties = {
  display: 'grid',
  minWidth: '1180px',
  width: '100%',
};

const rowStyle: CSSProperties = {
  display: 'grid',
  gridTemplateColumns: 'minmax(16rem, 2fr) repeat(4, minmax(7rem, 0.85fr)) minmax(16rem, 1.6fr) minmax(12rem, 1.15fr) minmax(16rem, 1.5fr)',
};

const headerRowStyle: CSSProperties = {
  ...rowStyle,
  position: 'sticky',
  top: 0,
  zIndex: 2,
};

const actionCellStyle: CSSProperties = {
  position: 'sticky',
  right: 0,
  zIndex: 1,
};

const actionHeaderStyle: CSSProperties = {
  ...actionCellStyle,
  zIndex: 3,
};

export function OffersTable({ offers, selectedOfferId, compareOfferIds, onInspect, onSelect, onCompareToggle, onSortField }: OffersTableProps) {
  const visible = offers.slice(0, boundedRenderCount);
  return (
    <div className="quote-table-shell" style={tableShellStyle} aria-label="Responsive quote offer table region">
      <div className="quote-table" role="table" aria-label="Quote offer comparison" aria-rowcount={offers.length} style={tableGridStyle}>
        <div role="row" className="quote-table__row quote-table__row--head" style={headerRowStyle}>
          <span role="columnheader">Product</span>
          <SortableHeader label="Rate" field="rate" onSortField={onSortField} />
          <SortableHeader label="APR" field="apr" onSortField={onSortField} />
          <SortableHeader label="Payment" field="payment" onSortField={onSortField} />
          <span role="columnheader">Lock</span>
          <span role="columnheader">Rationale</span>
          <span role="columnheader">Flags</span>
          <span role="columnheader" className="quote-table__actions-cell" style={actionHeaderStyle}>Actions</span>
        </div>
        {visible.map((offer) => {
          const selected = selectedOfferId === offer.offerId;
          const displayName = offerDisplayName(offer);
          return (
            <div key={offer.offerId} role="row" className={selected ? 'quote-table__row quote-table__row--selected' : 'quote-table__row'} aria-selected={selected} tabIndex={0} onKeyDown={(event) => handleRowKey(event, offer, onSelect, onCompareToggle)} style={rowStyle}>
              <span role="cell">
                <strong>{displayName}</strong>
                <br />Price: {valueText(offer.price)} · Investor: {valueText(offer.investor)}
              </span>
              <span role="cell">{valueText(offer.rate)}</span>
              <span role="cell">{valueText(offer.apr)}</span>
              <span role="cell">{valueText(offer.payment)}</span>
              <span role="cell">{valueText(offer.lockPeriodDays)}</span>
              <span role="cell"><ChipList label={`${displayName} rationale`} values={visibleOfferEvidenceValues(offer.rationaleChips)} /></span>
              <span role="cell"><ChipList label={`${displayName} flags`} values={visibleOfferEvidenceValues(offer.scenarioFlags)} /></span>
              <span role="cell" className="quick-quote-state quote-table__actions-cell" style={actionCellStyle}>
                <label><input type="radio" name="selected-offer" aria-label={`Select offer ${displayName}`} checked={selected} onChange={() => onSelect(offer)} /> Select offer</label>
                <label><input type="checkbox" aria-label={`Compare offer ${displayName}`} checked={compareOfferIds.includes(offer.offerId)} onChange={() => onCompareToggle(offer.offerId)} /> Compare offers</label>
                <button type="button" aria-label={`Inspect explanation for offer ${displayName}`} onMouseEnter={() => onInspect(offer)} onFocus={() => onInspect(offer)} onClick={() => onInspect(offer)}>Inspect explanation</button>
              </span>
            </div>
          );
        })}
        {offers.length > boundedRenderCount ? <p role="status">Showing first {boundedRenderCount} offers from a {offers.length} offer result set.</p> : null}
      </div>
    </div>
  );
}

function SortableHeader({ label, field, onSortField }: { label: string; field: OfferSortField; onSortField: (field: OfferSortField, additive: boolean) => void }) {
  return <button type="button" role="columnheader" onClick={(event) => onSortField(field, event.shiftKey)}>{label}</button>;
}

function handleRowKey(event: KeyboardEvent, offer: OfferSummary, onSelect: (offer: OfferSummary) => void, onCompareToggle: (offerId: string) => void) {
  if (isNestedInteractiveTarget(event.target)) return;
  if (event.key === 'Enter') {
    event.preventDefault();
    onSelect(offer);
  }
  if (event.key === ' ') {
    event.preventDefault();
    onCompareToggle(offer.offerId);
  }
}

function isNestedInteractiveTarget(target: EventTarget | null) {
  return target instanceof Element && Boolean(target.closest('button, input, select, textarea, a, label, [role="button"], [role="menuitem"], [role="checkbox"], [role="radio"]'));
}
