import type { LockConfirmationResult, LockWorkflowView } from '../../lib/api/quoteRuns';
import { dateTimeText } from './lockWorkflowUtils';

export function ConfirmLockDialog({ open, workflow, disclosuresAccepted, confirmation, confirming, onCancel, onConfirm }: { open: boolean; workflow: LockWorkflowView; disclosuresAccepted: boolean; confirmation: LockConfirmationResult | null; confirming: boolean; onCancel: () => void; onConfirm: () => void }) {
  if (!open) return null;
  return (
    <section role="dialog" aria-modal="true" aria-labelledby="confirm-lock-heading" className="panel">
      <h2 id="confirm-lock-heading">Confirm Lock</h2>
      <p>Confirm backend-returned lock terms and disclosure acceptance before submitting.</p>
      <dl className="status-grid">
        <dt>Note rate</dt><dd>{workflow.terms.noteRate}</dd>
        <dt>Final price</dt><dd>{workflow.terms.finalPriceBps} bps</dd>
        <dt>Expiration</dt><dd>{dateTimeText(workflow.terms.expiresAt)}</dd>
        <dt>Lock ID preview</dt><dd><code>{workflow.lockIdPreview}</code></dd>
        <dt>Disclosures accepted</dt><dd>{disclosuresAccepted ? 'Yes' : 'No'}</dd>
      </dl>
      {confirmation?.status === 'CONFLICT' ? <div role="alert">Conflict: {confirmation.conflictResolution ?? confirmation.message}</div> : null}
      <button type="button" disabled={!disclosuresAccepted || workflow.lockDisabled || confirming} onClick={onConfirm}>{confirming ? 'Confirming...' : 'Confirm Lock'}</button>
      <button type="button" className="button-secondary" onClick={onCancel}>Cancel</button>
    </section>
  );
}
