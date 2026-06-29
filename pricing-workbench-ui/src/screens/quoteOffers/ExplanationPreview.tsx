import { ChipList } from '../../components/ChipList';
import type { OfferSummary } from '../../lib/api/offers';
import { businessFacingText } from '../../lib/utils/businessFacingText';

export function ExplanationPreview({ offer, onViewFull }: { offer: OfferSummary | null; onViewFull: (offerId: string) => void }) {
  if (!offer) {
    return (
      <aside className="panel" aria-labelledby="explanation-preview-heading">
        <h2 id="explanation-preview-heading">Explanation preview</h2>
        <p>Choose an offer to preview pricing rationale and borrower scenario flags.</p>
      </aside>
    );
  }

  const unavailable = offer.explanationStatus !== 'AVAILABLE';
  return (
    <aside className="panel" aria-labelledby="explanation-preview-heading">
      <h2 id="explanation-preview-heading">Explanation preview</h2>
      {unavailable ? <div className="banner banner--blocked" role="alert">Explanation is not available yet.</div> : null}
      <ChipList label="Rationale lines" values={offer.rationaleChips} />
      <ChipList label="Scenario flags" values={offer.scenarioFlags} />
      <ChipList label="Review references" values={(offer.upstreamRefs ?? []).map(businessFacingText)} />
      <ChipList label="Snapshot references" values={(offer.snapshotRefs ?? []).map(businessFacingText)} />
      <button type="button" aria-label={`Inspect full explanation for offer ${offer.offerId}`} onClick={() => onViewFull(offer.offerId)}>Inspect full explanation</button>
    </aside>
  );
}
