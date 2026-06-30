import { ChipList } from '../../components/ChipList';
import type { OfferSummary } from '../../lib/api/offers';
import { businessFacingText } from '../../lib/utils/businessFacingText';
import { visibleOfferEvidenceValues, visibleOfferReferenceValues } from './offerComparison';

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
  const reviewReferences = visibleOfferReferenceValues(offer.upstreamRefs).map(businessFacingText);
  const snapshotReferences = visibleOfferReferenceValues(offer.snapshotRefs).map(businessFacingText);
  return (
    <aside className="panel" aria-labelledby="explanation-preview-heading">
      <h2 id="explanation-preview-heading">Explanation preview</h2>
      {unavailable ? <div className="banner banner--blocked" role="alert">Explanation is not available yet.</div> : null}
      <ChipList label="Rationale lines" values={visibleOfferEvidenceValues(offer.rationaleChips)} />
      <ChipList label="Scenario flags" values={visibleOfferEvidenceValues(offer.scenarioFlags)} />
      {reviewReferences.length > 0 ? <ChipList label="Review references" values={reviewReferences} /> : null}
      {snapshotReferences.length > 0 ? <ChipList label="Snapshot references" values={snapshotReferences} /> : null}
      <button type="button" aria-label={`Inspect full explanation for offer ${offer.offerId}`} onClick={() => onViewFull(offer.offerId)}>Inspect full explanation</button>
    </aside>
  );
}
