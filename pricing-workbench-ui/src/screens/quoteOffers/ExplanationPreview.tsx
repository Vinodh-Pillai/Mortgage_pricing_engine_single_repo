import { ChipList } from '../../components/ChipList';
import type { OfferSummary } from '../../lib/api/offers';

export function ExplanationPreview({ offer, onViewFull }: { offer: OfferSummary | null; onViewFull: (offerId: string) => void }) {
  if (!offer) {
    return (
      <aside className="panel" aria-labelledby="explanation-preview-heading">
        <h2 id="explanation-preview-heading">Explanation preview</h2>
        <p>Choose an offer to preview rationale, scenario flags, and upstream references.</p>
      </aside>
    );
  }

  const unavailable = offer.explanationStatus !== 'AVAILABLE';
  return (
    <aside className="panel" aria-labelledby="explanation-preview-heading">
      <h2 id="explanation-preview-heading">Explanation preview</h2>
      {unavailable ? <div className="banner banner--blocked" role="alert">Explanation unavailable from the connected boundary.</div> : null}
      <ChipList label="Rationale lines" values={offer.rationaleChips} />
      <ChipList label="Scenario flags" values={offer.scenarioFlags} />
      <ChipList label="Upstream references" values={offer.upstreamRefs ?? []} />
      <ChipList label="Snapshot references" values={offer.snapshotRefs ?? []} />
      <button type="button" onClick={() => onViewFull(offer.offerId)}>View Full Explanation</button>
    </aside>
  );
}
