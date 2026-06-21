import type { LockWorkflowAction, LockWorkflowStatus } from '../../lib/api/quoteRuns';
import { valueText } from './lockWorkflowUtils';

export function PostLockActions({ status, actions, onSelect }: { status: LockWorkflowStatus; actions: LockWorkflowAction[]; onSelect: (action: LockWorkflowAction) => void }) {
  const postLockAvailable = ['CONFIRMED', 'EXTENDED', 'RELOCKED', 'FLOAT_DOWN'].includes(status);
  return (
    <section className="panel" aria-labelledby="post-lock-actions-heading">
      <h2 id="post-lock-actions-heading">Post-Lock Actions</h2>
      <p>Available after confirmation or represented as backend eligibility evidence. Current state: {status}</p>
      {!postLockAvailable ? <p className="field-help">Confirm the selected lock before extension, relock, or float-down actions can be requested.</p> : null}
      <ul className="offer-list">
        {actions.map((action) => (
          <li key={action.action}>
            <strong>{action.label}</strong> <span className="trace-badge">{action.eligible ? 'eligible' : 'blocked'}</span>
            <p>{action.terms}</p>
            <p>Fee: {valueText(action.fee)} | Max days: {valueText(action.maxDays)} | Approval: {action.approvalRequired ? 'required' : 'not required'}</p>
            {action.blocker ? <p role="alert">{action.blocker}</p> : null}
            <button type="button" disabled={!action.eligible || !postLockAvailable} onClick={() => onSelect(action)}>{action.label}</button>
          </li>
        ))}
      </ul>
    </section>
  );
}
