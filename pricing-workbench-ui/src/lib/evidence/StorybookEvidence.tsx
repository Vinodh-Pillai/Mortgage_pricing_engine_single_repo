import type { EvidenceManifest } from './types';

export function EvidenceViewer({ manifest }: { manifest: EvidenceManifest }) {
  return (
    <section aria-label="Evidence timeline">
      <h2>Evidence timeline</h2>
      <p>{manifest.summary.totalScreens} screen(s), {manifest.summary.statesCovered.length} state(s) covered.</p>
      <ol>
        {manifest.evidence.map((entry) => (
          <li key={`${entry.screenId}-${entry.state}-${entry.timestamp}`}>
            <strong>{entry.screenId}</strong> — {entry.state} — <code>{entry.file}</code>
            {entry.screenshotRef ? <img alt={`${entry.screenId} ${entry.state} evidence`} src={entry.screenshotRef} /> : null}
          </li>
        ))}
      </ol>
      {manifest.summary.missingStates.length > 0 ? <p>Missing states: {manifest.summary.missingStates.join(', ')}</p> : null}
    </section>
  );
}
