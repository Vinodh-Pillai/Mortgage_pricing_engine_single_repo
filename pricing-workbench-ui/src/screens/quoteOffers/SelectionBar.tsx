import { ChipList } from '../../components/ChipList';
import type { OfferComparisonView, OfferSummary } from '../../lib/api/offers';
import { valueText } from './offerComparison';

export function SelectionBar({ runId, selectedOffer, comparison, compareOfferIds, onNavigateDetail, onNavigateLock }: { runId: string; selectedOffer: OfferSummary | null; comparison: OfferComparisonView; compareOfferIds: string[]; onNavigateDetail: (offerId: string) => void; onNavigateLock: () => void }) {
  if (!selectedOffer) return null;
  const commitBlocked = comparison.commitBlocked || selectedOffer.commitBlocked;
  const requiredFacts = selectedOffer.requiredFacts?.length ? selectedOffer.requiredFacts : comparison.requiredFacts ?? [];
  return (
    <section className="selection-bar" aria-label="Selected offer actions">
      <div>
        <strong>{selectedOffer.productLabel ?? selectedOffer.offerId}</strong>
        <span>Run {runId} | rate {valueText(selectedOffer.rate)} | APR {valueText(selectedOffer.apr)} | compare {compareOfferIds.length}/4</span>
      </div>
      {commitBlocked ? (
        <div className="banner banner--blocked" role="alert">
          <strong>Commit blocked</strong>
          <ChipList label="Required facts" values={requiredFacts} />
        </div>
      ) : null}
      <div className="quick-quote-state">
        <button type="button" onClick={() => onNavigateDetail(selectedOffer.offerId)}>Compare Detail</button>
        <button type="button" disabled={commitBlocked} onClick={onNavigateLock}>Lock Terms</button>
      </div>
    </section>
  );
}
