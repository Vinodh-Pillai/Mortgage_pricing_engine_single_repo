import { useEffect, useMemo, useState } from 'react';
import type { OfferComparisonView, OfferSummary } from '../../lib/api/offers';
import type { ScreenProps } from '../contract/ScreenProps';
import { BlockedOffers } from './BlockedOffers';
import { EmptyOffers } from './EmptyOffers';
import { ExplanationPreview } from './ExplanationPreview';
import { deterministicOfferComparison } from './fixtures';
import { OfferCard } from './OfferCard';
import { defaultOfferFilters, defaultOfferSort, filterOffers, offerOptions, sortOffers, stateForComparison, toggleCompareOffer, type OfferSortField } from './offerComparison';
import { OffersTable } from './OffersTable';
import { OffersToolbar } from './OffersToolbar';
import { SelectionBar } from './SelectionBar';

export type QuoteOffersScreenProps = Partial<ScreenProps> & {
  comparison?: OfferComparisonView;
  onNavigate?: (path: string) => void;
};

export default function QuoteOffersScreen({ tenantId = 'tenant-fixture', runId, uiTraceId = 'pii-24-s10-local-trace', comparison = deterministicOfferComparison, onEvidenceCapture, onNavigate }: QuoteOffersScreenProps) {
  const activeRunId = runId ?? comparison.runId;
  const [sort, setSort] = useState(defaultOfferSort);
  const [filters, setFilters] = useState(defaultOfferFilters);
  const [selectedOfferId, setSelectedOfferId] = useState<string | null>(comparison.selectedOfferId);
  const [compareOfferIds, setCompareOfferIds] = useState<string[]>([]);
  const [previewOffer, setPreviewOffer] = useState<OfferSummary | null>(comparison.offers[0] ?? null);
  const [viewMode, setViewMode] = useState<'table' | 'cards'>('table');
  const visibleOffers = useMemo(() => sortOffers(filterOffers(comparison.offers, filters), sort), [comparison.offers, filters, sort]);
  const selectedOffer = comparison.offers.find((offer) => offer.offerId === selectedOfferId) ?? null;
  const visualState = stateForComparison(comparison);
  const activeFilterCount = Object.values(filters).filter(Boolean).length;

  useEffect(() => {
    onEvidenceCapture?.({
      screenId: 'quote-offers',
      timestamp: new Date().toISOString(),
      state: visualState,
      dataRefs: [tenantId, activeRunId, comparison.uiTraceId, uiTraceId],
      blockers: visualState === 'blocked' ? comparison.requiredFacts ?? [] : [],
    });
  }, [activeRunId, comparison.requiredFacts, comparison.uiTraceId, onEvidenceCapture, tenantId, uiTraceId, visualState]);

  function selectOffer(offer: OfferSummary) {
    setSelectedOfferId(offer.offerId);
    setPreviewOffer(offer);
  }

  function navigate(path: string) {
    onNavigate?.(path);
  }

  function sortBy(field: OfferSortField, additive: boolean) {
    setSort((current) => ({ field, direction: current.field === field && !additive && current.direction === 'asc' ? 'desc' : 'asc' }));
  }

  return (
    <main className="quote-offers-screen" aria-labelledby="quote-offers-title">
      <section className="hero" aria-labelledby="quote-offers-title">
        <p className="eyebrow">Offer comparison | PII-24-S10</p>
        <h1 id="quote-offers-title">Compare Offers</h1>
        <p>Review connected ranked offers without calculating rates, eligibility, or investor decisions in the UI.</p>
      </section>

      {visualState === 'empty' ? <EmptyOffers fallbackReason={comparison.fallbackReason} /> : null}
      {visualState === 'blocked' ? <BlockedOffers reason={comparison.fallbackReason} requiredFacts={comparison.requiredFacts} backendRefs={comparison.backendRefs} /> : null}
      {visualState === 'ready' ? (
        <>
          <OffersToolbar
            sort={sort}
            filters={filters}
            productFamilies={offerOptions(comparison.offers, (offer) => offer.productFamily ?? offer.productLabel)}
            investors={offerOptions(comparison.offers, (offer) => offer.investor)}
            lockPeriods={offerOptions(comparison.offers, (offer) => offer.lockPeriodDays)}
            eligibilityStates={offerOptions(comparison.offers, (offer) => offer.eligibilityStatus)}
            viewMode={viewMode}
            activeFilterCount={activeFilterCount}
            onSortChange={setSort}
            onFiltersChange={setFilters}
            onViewModeChange={setViewMode}
            onReset={() => setFilters(defaultOfferFilters)}
          />
          {visibleOffers.length === 0 ? <EmptyOffers fallbackReason="No offers match the active filters." /> : viewMode === 'table' ? (
            <OffersTable offers={visibleOffers} selectedOfferId={selectedOfferId} compareOfferIds={compareOfferIds} onInspect={setPreviewOffer} onSelect={selectOffer} onCompareToggle={(offerId) => setCompareOfferIds((ids) => toggleCompareOffer(ids, offerId))} onSortField={sortBy} />
          ) : (
            <div className="offer-grid" role="list" aria-label="Offer cards">
              {visibleOffers.map((offer) => <OfferCard key={offer.offerId} offer={offer} selected={selectedOfferId === offer.offerId} compared={compareOfferIds.includes(offer.offerId)} onInspect={setPreviewOffer} onSelect={selectOffer} onCompareToggle={(offerId) => setCompareOfferIds((ids) => toggleCompareOffer(ids, offerId))} />)}
            </div>
          )}
          <ExplanationPreview offer={previewOffer} onViewFull={(offerId) => navigate(`/quote/${encodeURIComponent(activeRunId)}/offers/${encodeURIComponent(offerId)}`)} />
          <SelectionBar runId={activeRunId} selectedOffer={selectedOffer} comparison={comparison} compareOfferIds={compareOfferIds} onNavigateDetail={(offerId) => navigate(`/quote/${encodeURIComponent(activeRunId)}/offers/${encodeURIComponent(offerId)}`)} onNavigateLock={() => navigate(`/quote/${encodeURIComponent(activeRunId)}/lock`)} />
        </>
      ) : null}
    </main>
  );
}
