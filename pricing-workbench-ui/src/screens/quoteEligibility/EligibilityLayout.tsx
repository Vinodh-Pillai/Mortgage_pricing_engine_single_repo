import type { EligibilityModuleView } from './fixtures';
import type { CSSProperties } from 'react';

type Navigate = (path: string) => void;

const decisionCopy = {
  ELIGIBLE: { label: 'ELIGIBLE', background: '#dcfce7', color: '#14532d', border: '#86efac' },
  INELIGIBLE: { label: 'INELIGIBLE', background: '#fee2e2', color: '#7f1d1d', border: '#fca5a5' },
  CONDITIONAL: { label: 'CONDITIONAL', background: '#fef3c7', color: '#78350f', border: '#fcd34d' },
} as const;

export function EligibilityLayout({ module, runId, optionId, onNavigate }: { module: EligibilityModuleView; runId: string; optionId: string; onNavigate: Navigate }) {
  if (module.status === 'LOADING') return <LoadingState />;

  return (
    <main className="quote-eligibility-screen" aria-labelledby="quote-eligibility-title">
      <section className="hero" aria-labelledby="quote-eligibility-title">
        <p className="eyebrow">Eligibility explanation | PII-24-S13</p>
        <h1 id="quote-eligibility-title">Eligibility Explanation</h1>
        <p>Transparent backend eligibility response for a selected quote option. This screen renders API-shaped decisions, reason codes, facts, overlays, and cache metadata without local eligibility calculations.</p>
        <div style={layoutGrid} aria-label="Eligibility route context">
          <span><strong>Run</strong><br /><code>{runId}</code></span>
          <span><strong>Option</strong><br /><code>{optionId}</code></span>
          <span><strong>Decision ID</strong><br /><code>{module.decisionId}</code></span>
          <span><strong>Correlation</strong><br /><code>{module.correlationId}</code></span>
        </div>
        <button type="button" className="button-secondary" onClick={() => onNavigate(`/quote/${encodeURIComponent(runId)}/offers`)}>Back to Offers</button>
      </section>

      {module.status === 'BLOCKED' ? <BlockedState module={module} onNavigate={onNavigate} /> : null}
      <DecisionSummary module={module} />
      <div style={layoutGrid}>
        <BlockerList module={module} />
        <RequiredNextFacts module={module} onNavigate={onNavigate} />
      </div>
      <div style={layoutGrid}>
        <FactTraceability module={module} onNavigate={onNavigate} />
        <OverlayReferences module={module} onNavigate={onNavigate} />
        <CacheHealth module={module} />
      </div>
    </main>
  );
}

function LoadingState() {
  return (
    <main className="quote-eligibility-screen" aria-labelledby="quote-eligibility-title">
      <section className="hero" aria-busy="true">
        <p className="eyebrow">Eligibility explanation | PII-24-S13</p>
        <h1 id="quote-eligibility-title">Eligibility Explanation</h1>
        <div className="ds-skeleton" style={{ minHeight: '3rem' }} role="status" aria-label="Loading eligibility decision" />
      </section>
    </main>
  );
}

function BlockedState({ module, onNavigate }: { module: EligibilityModuleView; onNavigate: Navigate }) {
  return (
    <section className="banner banner--blocked" role="alert" aria-labelledby="eligibility-blocked-heading">
      <h2 id="eligibility-blocked-heading">Eligibility view blocked</h2>
      <p>{module.explanation}</p>
      <button type="button" onClick={() => onNavigate(`/quote/start?step=review&highlight=${encodeURIComponent(module.requiredNextFacts[0]?.factRef ?? 'eligibility-service')}`)}>Complete Missing Facts</button>
    </section>
  );
}

function DecisionSummary({ module }: { module: EligibilityModuleView }) {
  const badge = decisionCopy[module.decision];
  return (
    <section className="panel" aria-labelledby="decision-summary-heading">
      <div className="panel-heading-row">
        <div>
          <h2 id="decision-summary-heading">Decision Summary</h2>
          <p>{module.explanation}</p>
        </div>
        <span role="status" aria-label={`Eligibility decision ${badge.label}`} style={{ ...badgeStyle, background: badge.background, borderColor: badge.border, color: badge.color }}>{badge.label}</span>
      </div>
      <dl className="status-grid">
        <dt>Decision timestamp</dt><dd>{module.timestamp}</dd>
        <dt>Cache freshness</dt><dd><CacheBadge freshness={module.cache.freshness} /></dd>
        <dt>Backend refs</dt><dd><InlineRefs refs={module.backendRefs} /></dd>
      </dl>
      <details>
        <summary>View All Decisions</summary>
        <ul className="offer-list">
          {module.allDecisions.map((decision) => <li key={decision.decisionId}><strong>{decision.optionId}</strong> <span className="trace-badge">{decision.decision}</span><p>{decision.summary}</p></li>)}
        </ul>
      </details>
    </section>
  );
}

function BlockerList({ module }: { module: EligibilityModuleView }) {
  return (
    <section className="panel" aria-labelledby="blocker-list-heading">
      <h2 id="blocker-list-heading">Reason Codes and Blockers</h2>
      {module.reasonCodes.length === 0 ? <p>No reason codes returned by the backend response.</p> : (
        <table className="ds-table" aria-label="Eligibility reason codes">
          <thead><tr><th scope="col">Reason Code</th><th scope="col">Fact Ref</th><th scope="col">Message</th><th scope="col">Severity</th><th scope="col">Overlay Ref</th></tr></thead>
          <tbody>
            {module.reasonCodes.map((reason) => (
              <tr key={`${reason.reasonCode}-${reason.factRef ?? reason.message}`}>
                <td><details><summary><code>{reason.reasonCode}</code></summary><p>{reason.description}</p><p><strong>Rule:</strong> <code>{reason.ruleRef ?? 'N/A'}</code></p><p><strong>Remediation:</strong> {reason.remediation ?? 'No remediation returned.'}</p></details></td>
                <td><code>{reason.factRef ?? 'N/A'}</code></td>
                <td>{reason.message}</td>
                <td><span className="trace-badge">{reason.severity}</span></td>
                <td><code>{reason.overlayRef ?? 'N/A'}</code></td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      <button type="button" className="button-secondary" onClick={() => void navigator.clipboard?.writeText(module.reasonCodes.map((reason) => `${reason.reasonCode}: ${reason.message}`).join('\n'))}>Copy Blockers</button>
    </section>
  );
}

function FactTraceability({ module, onNavigate }: { module: EligibilityModuleView; onNavigate: Navigate }) {
  return (
    <section className="panel" aria-labelledby="fact-trace-heading">
      <h2 id="fact-trace-heading">Fact Traceability</h2>
      {module.facts.length === 0 ? <p>No input facts were returned.</p> : (
        <ul className="offer-list">
          {module.facts.map((fact) => (
            <li key={fact.factRef} className={fact.quality === 'missing' ? 'evidence-card--blocked' : undefined}>
              <strong>{fact.label}</strong> <span className="trace-badge">{fact.quality}</span>
              <dl className="status-grid">
                <dt>Fact ref</dt><dd><code>{fact.factRef}</code></dd>
                <dt>Value</dt><dd>{valueText(fact.value)}</dd>
                <dt>Source</dt><dd>{fact.source}</dd>
                <dt>Rules</dt><dd><InlineRefs refs={fact.linkedRuleRefs} /></dd>
              </dl>
              <button type="button" onClick={() => onNavigate(`/quote/start?step=${encodeURIComponent(fact.intakeStep ?? 'review')}&highlight=${encodeURIComponent(fact.factRef)}`)}>Open Intake Fact</button>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function OverlayReferences({ module, onNavigate }: { module: EligibilityModuleView; onNavigate: Navigate }) {
  return (
    <section className="panel" aria-labelledby="overlay-refs-heading">
      <h2 id="overlay-refs-heading">Overlay References</h2>
      {module.overlayRefs.length === 0 ? <p>No overlay references were returned for this decision.</p> : (
        <ul className="offer-list">
          {module.overlayRefs.map((overlay) => (
            <li key={overlay.overlayId}>
              <strong>{overlay.overlayId}</strong> <span className="trace-badge">{overlay.overlayType}</span>
              <dl className="status-grid">
                <dt>Version</dt><dd><code>{overlay.version}</code></dd>
                <dt>Rule ref</dt><dd><code>{overlay.ruleRef}</code></dd>
                <dt>Effect</dt><dd>{overlay.effect}</dd>
              </dl>
              <button type="button" onClick={() => onNavigate(overlay.target)}>Open Overlay Evidence</button>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function CacheHealth({ module }: { module: EligibilityModuleView }) {
  return (
    <section className="panel" aria-labelledby="cache-health-heading">
      <h2 id="cache-health-heading">Cache Health</h2>
      <dl className="status-grid">
        <dt>Freshness</dt><dd><CacheBadge freshness={module.cache.freshness} /></dd>
        <dt>Cache ref</dt><dd><code>{module.cache.cacheRef}</code></dd>
        <dt>Observed at</dt><dd>{valueText(module.cache.observedAt)}</dd>
        <dt>TTL seconds</dt><dd>{valueText(module.cache.ttlSeconds)}</dd>
        <dt>Refresh</dt><dd>{module.cache.refreshPermitted ? 'Backend refresh action permitted by response.' : 'Refresh not permitted by response.'}</dd>
      </dl>
      <InlineRefs refs={module.cache.dependencyRefs} />
    </section>
  );
}

function RequiredNextFacts({ module, onNavigate }: { module: EligibilityModuleView; onNavigate: Navigate }) {
  if (module.decision === 'ELIGIBLE' && module.requiredNextFacts.length === 0) {
    return <section className="panel" aria-labelledby="next-facts-heading"><h2 id="next-facts-heading">Required Next Facts</h2><p>No required next facts returned.</p></section>;
  }

  return (
    <section className="panel" aria-labelledby="next-facts-heading">
      <h2 id="next-facts-heading">Required Next Facts</h2>
      <ul className="offer-list">
        {module.requiredNextFacts.map((fact) => (
          <li key={fact.factRef} className="evidence-card--blocked">
            <strong>{fact.label}</strong>
            <p>{fact.reason}</p>
            <button type="button" onClick={() => onNavigate(`/quote/start?step=${encodeURIComponent(fact.intakeStep)}&highlight=${encodeURIComponent(fact.factRef)}`)}>Complete in Intake</button>
          </li>
        ))}
      </ul>
    </section>
  );
}

function CacheBadge({ freshness }: { freshness: string }) {
  const colors: Record<string, { background: string; color: string; border: string }> = {
    FRESH: { background: '#dcfce7', color: '#14532d', border: '#86efac' },
    STALE: { background: '#fef3c7', color: '#78350f', border: '#fcd34d' },
    EXPIRED: { background: '#fee2e2', color: '#7f1d1d', border: '#fca5a5' },
    MISSING: { background: '#e2e8f0', color: '#334155', border: '#cbd5e1' },
  };
  const color = colors[freshness] ?? colors.MISSING;
  return <span className="trace-badge" style={{ background: color.background, color: color.color, borderColor: color.border }}>{freshness}</span>;
}

function InlineRefs({ refs }: { refs: string[] }) {
  if (refs.length === 0) return <span>N/A</span>;
  return <div className="copyable-ref-list"><ul>{refs.map((ref) => <li key={ref}><code>{ref}</code></li>)}</ul></div>;
}

function valueText(value: string | number | null | undefined) {
  if (value === null || value === undefined || value === '') return 'N/A';
  return String(value);
}

const layoutGrid = {
  display: 'grid',
  gap: '1rem',
  gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 18rem), 1fr))',
} satisfies CSSProperties;

const badgeStyle = {
  alignSelf: 'start',
  border: '1px solid',
  borderRadius: '999px',
  display: 'inline-flex',
  fontWeight: 900,
  letterSpacing: '0.08em',
  padding: '0.5rem 0.85rem',
} satisfies CSSProperties;
