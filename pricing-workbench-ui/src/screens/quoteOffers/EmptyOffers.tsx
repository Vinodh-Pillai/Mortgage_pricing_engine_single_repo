export function EmptyOffers({ fallbackReason }: { fallbackReason?: string | null }) {
  return (
    <section className="panel" aria-labelledby="empty-offers-heading">
      <h2 id="empty-offers-heading">No offers available</h2>
      <p>{fallbackReason ?? 'No ranked offers were returned for this quote run.'}</p>
      <ul>
        <li><a href="/quote/start">Adjust intake</a></li>
        <li><a href="/quote/start">Check eligibility inputs</a></li>
        <li><a href="/quality/validation">Contact pricing admin</a></li>
      </ul>
    </section>
  );
}
