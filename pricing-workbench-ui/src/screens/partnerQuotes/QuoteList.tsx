import type { PartnerQuoteFilters, PartnerQuoteRow } from './types';

type QuoteListProps = {
  quotes: PartnerQuoteRow[];
  filters: PartnerQuoteFilters;
  selectedQuoteId: string | null;
  onFiltersChange: (filters: PartnerQuoteFilters) => void;
  onSelectQuote: (quoteId: string) => void;
};

export function QuoteList({ quotes, filters, selectedQuoteId, onFiltersChange, onSelectQuote }: QuoteListProps) {
  const partners = uniqueValues(quotes.map((quote) => quote.partner));
  const filteredQuotes = filterAndSortQuotes(quotes, filters);

  return (
    <section className="panel" aria-labelledby="partner-quote-list-heading">
      <div className="panel-heading-row">
        <div>
          <p className="eyebrow">Quote list</p>
          <h2 id="partner-quote-list-heading">Partner quote list</h2>
        </div>
      </div>

      <div className="offer-toolbar" aria-label="Partner quote filters">
        <FilterSelect label="Partner filter" value={filters.partner} values={partners} onChange={(partner) => onFiltersChange({ ...filters, partner })} />
        <FilterSelect label="Status filter" value={filters.status} values={['SUBMITTED', 'PRICING', 'PRICED', 'LOCKED', 'COMMITTED', 'FUNDED', 'REJECTED', 'WITHDRAWN']} onChange={(status) => onFiltersChange({ ...filters, status })} />
        <FilterSelect label="SLA state filter" value={filters.slaState} values={['ON_TRACK', 'AT_RISK', 'BREACHED']} onChange={(slaState) => onFiltersChange({ ...filters, slaState })} />
        <FilterSelect label="Lock state filter" value={filters.lockState} values={['UNLOCKED', 'LOCKED', 'EXPIRED', 'RELOCKED', 'FLOAT_DOWN']} onChange={(lockState) => onFiltersChange({ ...filters, lockState })} />
        <label>Date range
          <select value={filters.dateRange} onChange={(event) => onFiltersChange({ ...filters, dateRange: event.target.value })}>
            <option value="all">All refs</option>
            <option value="created-ref">Created refs</option>
            <option value="updated-ref">Updated refs</option>
          </select>
        </label>
        <label>Sort
          <select value={filters.sort} onChange={(event) => onFiltersChange({ ...filters, sort: event.target.value as PartnerQuoteFilters['sort'] })}>
            <option value="created">Created</option>
            <option value="updated">Updated</option>
            <option value="sla">SLA</option>
            <option value="borrower">Borrower</option>
          </select>
        </label>
      </div>

      {filteredQuotes.length === 0 ? <p role="status">No partner quotes match the selected filters.</p> : (
        <div className="quote-table" role="table" aria-label="Partner quotes list">
          <div role="row" className="quote-table__row quote-table__row--head">
            <span role="columnheader">Quote ID</span>
            <span role="columnheader">Borrower Label</span>
            <span role="columnheader">Status</span>
            <span role="columnheader">SLA State</span>
            <span role="columnheader">Lock State</span>
            <span role="columnheader">Error Flags</span>
            <span role="columnheader">Partner</span>
            <span role="columnheader">Created</span>
            <span role="columnheader">Updated</span>
            <span role="columnheader">Action</span>
          </div>
          {filteredQuotes.map((quote) => (
            <div key={quote.quoteId} role="row" className={selectedQuoteId === quote.quoteId ? 'quote-table__row quote-table__row--selected' : 'quote-table__row'}>
              <span role="cell"><code>{quote.quoteId}</code></span>
              <span role="cell">{quote.borrowerLabel}</span>
              <span role="cell">{quote.status}</span>
              <span role="cell">{quote.slaState}</span>
              <span role="cell">{quote.lockState}</span>
              <span role="cell"><button type="button" className="button-secondary" onClick={() => onSelectQuote(quote.quoteId)}>{quote.errorFlags.length ? quote.errorFlags.join(', ') : 'No error flags'}</button></span>
              <span role="cell">{quote.partner}</span>
              <span role="cell"><code>{quote.createdRef}</code></span>
              <span role="cell"><code>{quote.updatedRef}</code></span>
              <span role="cell"><button type="button" onClick={() => onSelectQuote(quote.quoteId)}>Open quote detail</button></span>
            </div>
          ))}
        </div>
      )}
    </section>
  );
}

export function filterAndSortQuotes(quotes: PartnerQuoteRow[], filters: PartnerQuoteFilters) {
  return quotes
    .filter((quote) => filters.partner === 'all' || quote.partner === filters.partner)
    .filter((quote) => filters.status === 'all' || quote.status === filters.status)
    .filter((quote) => filters.slaState === 'all' || quote.slaState === filters.slaState)
    .filter((quote) => filters.lockState === 'all' || quote.lockState === filters.lockState)
    .filter((quote) => filters.dateRange === 'all' || quote.createdRef.includes(filters.dateRange) || quote.updatedRef.includes(filters.dateRange))
    .sort((left, right) => {
      if (filters.sort === 'updated') return left.updatedRef.localeCompare(right.updatedRef);
      if (filters.sort === 'sla') return left.slaState.localeCompare(right.slaState);
      if (filters.sort === 'borrower') return left.borrowerLabel.localeCompare(right.borrowerLabel);
      return left.createdRef.localeCompare(right.createdRef);
    });
}

function FilterSelect({ label, value, values, onChange }: { label: string; value: string; values: string[]; onChange: (value: string) => void }) {
  return (
    <label>{label}
      <select value={value} onChange={(event) => onChange(event.target.value)}>
        <option value="all">All</option>
        {values.map((option) => <option key={option} value={option}>{option}</option>)}
      </select>
    </label>
  );
}

function uniqueValues(values: string[]) {
  return Array.from(new Set(values));
}
