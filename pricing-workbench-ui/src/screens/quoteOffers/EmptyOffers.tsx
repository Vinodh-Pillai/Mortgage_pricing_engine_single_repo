import { businessFacingText } from '../../lib/utils/businessFacingText';

export function EmptyOffers({ fallbackReason }: { fallbackReason?: string | null }) {
  return (
    <section className="panel" aria-labelledby="empty-offers-heading">
      <h2 id="empty-offers-heading">No offers available</h2>
      <p>{businessFacingText(fallbackReason ?? 'No ranked offers were returned for this quote run.')}</p>
      <ul>
        <li><a href="/pipeline">Adjust intake</a></li>
        <li><a href="/pipeline">Check eligibility inputs</a></li>
        <li><a href="/pipeline">Review borrower facts</a></li>
      </ul>
    </section>
  );
}
