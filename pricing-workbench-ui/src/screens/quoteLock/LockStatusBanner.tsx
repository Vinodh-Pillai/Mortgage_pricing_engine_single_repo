import type { LockWorkflowStatus } from '../../lib/api/quoteRuns';
import { countdownWarning, valueText } from './lockWorkflowUtils';

export function LockStatusBanner({ status, lockId, expiresAt, extensionCount }: { status: LockWorkflowStatus; lockId: string | null | undefined; expiresAt: string; extensionCount: number }) {
  const warning = countdownWarning(expiresAt);
  const statusText = status === 'FLOAT_DOWN' ? 'FLOAT-DOWN' : status;
  return (
    <section className={`banner banner--${warning.severity}`} role="status" aria-label="Lock status banner">
      <strong>{statusText}</strong> <span>{warning.text}</span>
      <dl className="status-grid">
        <dt>Lock ID</dt><dd><code>{valueText(lockId)}</code></dd>
        <dt>Countdown</dt><dd>{warning.label}</dd>
        <dt>Expiration</dt><dd>{new Date(expiresAt).toLocaleString()}</dd>
        <dt>Extensions</dt><dd>{extensionCount}</dd>
      </dl>
    </section>
  );
}
