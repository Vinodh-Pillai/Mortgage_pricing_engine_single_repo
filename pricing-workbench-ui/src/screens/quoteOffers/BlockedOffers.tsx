import { ChipList } from '../../components/ChipList';

export function BlockedOffers({ reason, requiredFacts = [], backendRefs = [] }: { reason?: string | null; requiredFacts?: string[]; backendRefs?: string[] }) {
  return (
    <section className="panel" aria-labelledby="blocked-offers-heading">
      <h2 id="blocked-offers-heading">Offer comparison needs connected facts</h2>
      <div className="banner banner--blocked" role="alert">{reason ?? 'The offer comparison response is blocked by missing upstream facts.'}</div>
      <ChipList label="Required facts" values={requiredFacts} />
      <ChipList label="Backend references" values={backendRefs} />
      <a href="/pipeline">Return to Intake</a>
    </section>
  );
}
