import { businessFacingText } from './shared';

const manualWorkflowRoute = '/pipeline';

export function AdvisoryUnavailable({ reason, onRetry }: { reason: string; onRetry: () => void }) {
  return (
    <section className="panel" aria-labelledby="ml-advisory-unavailable-heading">
      <div className="banner banner--blocked" role="alert">
        <strong id="ml-advisory-unavailable-heading">Advisory unavailable</strong>
        <span>Reason: {businessFacingText(reason)}</span>
        <span>Use manual pricing workflow while connected advisory evidence is unavailable.</span>
      </div>
      <button type="button" onClick={onRetry}>Retry advisory insights</button>
      <a className="button-secondary" href={manualWorkflowRoute}>Use manual pricing workflow</a>
    </section>
  );
}
