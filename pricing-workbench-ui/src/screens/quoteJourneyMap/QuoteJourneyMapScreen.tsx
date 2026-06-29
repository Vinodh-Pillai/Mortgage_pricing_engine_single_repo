import { deterministicQuoteJourneyMap } from './fixtures';

export default function QuoteJourneyMapScreen() {
  return (
    <main className="quote-journey-map-screen" aria-labelledby="quote-journey-map-title">
      <section className="hero hero--admin" aria-labelledby="quote-journey-map-title">
        <p className="eyebrow">Preview evidence page · non-production</p>
        <h1 id="quote-journey-map-title">Quote Journey Map preview</h1>
        <p>
          This deterministic preview/evidence page explains the quote journey map requested for Sarah/reviewer review.
          It does not calculate prices or claim production service connectivity.
        </p>
      </section>
      <section className="panel" aria-labelledby="quote-journey-map-nodes">
        <h2 id="quote-journey-map-nodes">Journey nodes</h2>
        <ol>
          {deterministicQuoteJourneyMap.nodes.map((node) => (
            <li key={node.nodeId}>
              <strong>{node.label}</strong> — {node.serviceName} — {node.status}. {node.freshness.message}
            </li>
          ))}
        </ol>
      </section>
    </main>
  );
}
