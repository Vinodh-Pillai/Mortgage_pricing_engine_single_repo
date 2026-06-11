import type { OfferFilters, OfferSort, OfferSortField } from './offerComparison';

type OffersToolbarProps = {
  sort: OfferSort;
  filters: OfferFilters;
  productFamilies: string[];
  investors: string[];
  lockPeriods: string[];
  eligibilityStates: string[];
  viewMode: 'table' | 'cards';
  activeFilterCount: number;
  onSortChange: (sort: OfferSort) => void;
  onFiltersChange: (filters: OfferFilters) => void;
  onViewModeChange: (viewMode: 'table' | 'cards') => void;
  onReset: () => void;
};

const sortFields: OfferSortField[] = ['rank', 'rate', 'apr', 'payment', 'confidence', 'rankScore'];

export function OffersToolbar({ sort, filters, productFamilies, investors, lockPeriods, eligibilityStates, viewMode, activeFilterCount, onSortChange, onFiltersChange, onViewModeChange, onReset }: OffersToolbarProps) {
  return (
    <div className="offer-toolbar" aria-label="Offer sort and filter controls">
      <label>
        Sort
        <select value={sort.field} onChange={(event) => onSortChange({ ...sort, field: event.target.value as OfferSortField })}>
          {sortFields.map((field) => <option key={field} value={field}>{field}</option>)}
        </select>
      </label>
      <label>
        Direction
        <select value={sort.direction} onChange={(event) => onSortChange({ ...sort, direction: event.target.value as OfferSort['direction'] })}>
          <option value="asc">Ascending</option>
          <option value="desc">Descending</option>
        </select>
      </label>
      <label>
        Product family
        <select value={filters.productFamily} onChange={(event) => onFiltersChange({ ...filters, productFamily: event.target.value })}>
          <option value="">Any</option>
          {productFamilies.map((value) => <option key={value} value={value}>{value}</option>)}
        </select>
      </label>
      <label>
        Investor
        <select value={filters.investor} onChange={(event) => onFiltersChange({ ...filters, investor: event.target.value })}>
          <option value="">Any</option>
          {investors.map((value) => <option key={value} value={value}>{value}</option>)}
        </select>
      </label>
      <label>
        Max rate
        <input inputMode="decimal" value={filters.rateMax} onChange={(event) => onFiltersChange({ ...filters, rateMax: event.target.value })} placeholder="No max" />
      </label>
      <label>
        Min confidence
        <input inputMode="numeric" value={filters.confidenceMin} onChange={(event) => onFiltersChange({ ...filters, confidenceMin: event.target.value })} placeholder="No min" />
      </label>
      <label>
        Lock period
        <select value={filters.lockPeriodDays} onChange={(event) => onFiltersChange({ ...filters, lockPeriodDays: event.target.value })}>
          <option value="">Any</option>
          {lockPeriods.map((value) => <option key={value} value={value}>{value}</option>)}
        </select>
      </label>
      <label>
        Eligibility
        <select value={filters.eligibilityStatus} onChange={(event) => onFiltersChange({ ...filters, eligibilityStatus: event.target.value })}>
          <option value="">Any</option>
          {eligibilityStates.map((value) => <option key={value} value={value}>{value}</option>)}
        </select>
      </label>
      <div className="quick-quote-state" aria-label="View mode">
        <button type="button" aria-pressed={viewMode === 'table'} onClick={() => onViewModeChange('table')}>Table</button>
        <button type="button" aria-pressed={viewMode === 'cards'} onClick={() => onViewModeChange('cards')}>Cards</button>
      </div>
      <button type="button" onClick={onReset}>Reset filters ({activeFilterCount})</button>
    </div>
  );
}
