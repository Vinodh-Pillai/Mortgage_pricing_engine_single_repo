import { ChipList } from '../../components/ChipList';
import { businessFacingText } from '../../lib/utils/businessFacingText';

export function BlockedOffers({ reason, requiredFacts = [], backendRefs = [] }: { reason?: string | null; requiredFacts?: string[]; backendRefs?: string[] }) {
  return (
    <section className="panel" aria-labelledby="blocked-offers-heading">
      <h2 id="blocked-offers-heading">Offer comparison needs connected facts</h2>
      <div className="banner banner--blocked" role="alert">{businessFacingText(reason ?? 'Offer comparison needs required borrower and pricing facts.')}</div>
      <ChipList label="Required facts" values={requiredFacts} />
      {backendRefs.length > 0 ? <p className="quote-intake-status">Connected pricing review is not ready yet.</p> : null}
      <a href="/pipeline">Return to Intake</a>
    </section>
  );
}
