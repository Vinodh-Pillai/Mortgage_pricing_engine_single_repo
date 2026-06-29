import type { LockWorkflowStatus } from '../../lib/api/quoteRuns';
import { countdownWarning, dateTimeText, valueText } from './lockWorkflowUtils';

export function LockStatusBanner({ status, lockId, expiresAt, extensionCount }: { status: LockWorkflowStatus; lockId: string | null | undefined; expiresAt: string | null | undefined; extensionCount: number }) {
  const warning = countdownWarning(expiresAt);
  const statusText = status === 'FLOAT_DOWN' ? 'FLOAT-DOWN' : status;
  const workflowMessage = status === 'READY' ? 'Ready to confirm' : status === 'CONFIRMED' ? 'Lock details returned' : warning.text;
  return (
    <section className={`banner banner--${warning.severity}`} role="status" aria-label="Lock status banner">
      <strong>{statusText}</strong> <span>{workflowMessage}</span>
      <dl className="status-grid">
        <dt>Lock ID</dt><dd><code>{valueText(lockId)}</code></dd>
        <dt>Countdown</dt><dd>{warning.label}</dd>
        <dt>Expiry:</dt><dd>{dateTimeText(expiresAt)}</dd>
        <dt>Extensions</dt><dd>{extensionCount}</dd>
      </dl>
    </section>
  );
}
