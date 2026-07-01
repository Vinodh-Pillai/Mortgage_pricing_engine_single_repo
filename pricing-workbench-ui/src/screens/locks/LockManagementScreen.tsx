import { useState } from 'react';
import { MajorFunctionalityPage, type EvidenceCapture, type FunctionalityPageConfig } from '../shared/MajorFunctionalityPage';
import type { ScreenVisualState } from '../contract/ScreenProps';

type LockRow = { id: string; borrowerRef: string; delivery: string; status: string };

export const lockManagementEvidenceTarget = '.local-harness/evidence/PII-25-S04/lock-management.json';

const lockConfig: FunctionalityPageConfig<LockRow> = {
  screenId: 'lock-management',
  evidenceTarget: lockManagementEvidenceTarget,
  breadcrumb: 'Locks / Management',
  eyebrow: 'Lock service',
  title: 'Lock Management',
  summary: 'Tracks lock requests, confirmed locks, expiring work, history, and investor delivery status using local preview data. Connected service actions are disabled until real lock records are available.',
  dataBoundary: 'lock-service: GET/POST /api/v1/locks',
  sections: [
    { id: 'active-locks', eyebrow: 'Active', title: 'Active Locks', summary: 'Confirmed and requested lock records.', status: 'ready', items: ['Confirmed locks', 'Requested locks', 'Owner refs'] },
    { id: 'lock-requests', eyebrow: 'Requests', title: 'Lock Requests', summary: 'Pending lock request queue.', status: 'needs-attention', items: ['Request ref', 'Approver', 'SLA ref'] },
    { id: 'expiring-soon', eyebrow: 'Expiring', title: 'Expiring Soon', summary: 'Expiring lock visibility.', status: 'blocked', items: ['Expiration ref', 'Extension owner', 'Investor cutoff ref'] },
    { id: 'history', eyebrow: 'History', title: 'History', summary: 'Lock event history and audit refs.', status: 'ready', items: ['Created', 'Extended', 'Cancelled'] },
    { id: 'bulk-actions', eyebrow: 'Bulk', title: 'Bulk Actions', summary: 'Bulk extend, cancel, and deliver actions.', status: 'needs-attention', items: ['Bulk extend', 'Bulk cancel', 'Bulk deliver'] },
    { id: 'investor-delivery', eyebrow: 'Delivery', title: 'Investor Delivery', summary: 'Investor delivery state and evidence.', status: 'empty', items: ['Delivery package', 'Acknowledgement', 'Exception ref'] },
  ],
  metrics: [
    { label: 'Statuses', value: '5', help: 'requested, confirmed, expired, cancelled, delivered' },
    { label: 'Bulk ops', value: '3', help: 'extend, cancel, deliver' },
    { label: 'Data source', value: 'Local preview', help: 'Connected lock-service actions are disabled in this UI preview.' },
  ],
  rows: [
    { id: 'lock-requested', borrowerRef: 'Preview borrower ref A', delivery: 'Not ready', status: 'requested' },
    { id: 'lock-confirmed', borrowerRef: 'Preview borrower ref B', delivery: 'Pending investor package', status: 'confirmed' },
    { id: 'lock-expired', borrowerRef: 'Preview borrower ref C', delivery: 'Needs review', status: 'expired' },
    { id: 'lock-cancelled', borrowerRef: 'Preview borrower ref D', delivery: 'Closed', status: 'cancelled' },
    { id: 'lock-delivered', borrowerRef: 'Preview borrower ref E', delivery: 'Acknowledged', status: 'delivered' },
  ],
  columns: [
    { key: 'borrowerRef', header: 'Borrower ref' },
    { key: 'delivery', header: 'Investor delivery' },
    { key: 'status', header: 'Status', render: (row) => <span className={`functionality-badge functionality-badge--${row.status}`}>{row.status}</span> },
  ],
  tableCaption: 'Lock management records',
  primaryActions: [{ id: 'request-lock-review', label: 'Start Lock Review (preview disabled)', variant: 'primary', disabled: true }],
  secondaryActions: [{ id: 'extend-expiring-lock-review', label: 'Stage Extension Review (preview disabled)', disabled: true }, { id: 'show-expiry-blockers', label: 'Show Expiry Blockers (preview only)' }, { id: 'extend-locks', label: 'Bulk extend (preview disabled)', disabled: true }, { id: 'cancel-locks', label: 'Bulk cancel (preview disabled)', variant: 'danger', disabled: true }, { id: 'deliver-locks', label: 'Bulk deliver (preview disabled)', disabled: true }],
  emptyMessage: 'No lock records are available for this workspace.',
  blockedMessage: 'Lock management is blocked until lock-service expiration and delivery references are available.',
  attentionMessage: 'Requested and expiring locks need operations review.',
  renderSpotlight: (onEvidence) => <LockOperationsSpotlight onEvidence={onEvidence} />,
};

export function LockManagementScreen({ visualState, onEvidenceCapture }: { visualState?: ScreenVisualState; onEvidenceCapture?: EvidenceCapture }) {
  return (
    <>
      <style>{`
        #lock-management-heading {
          position: static !important;
          display: block !important;
          width: auto !important;
          height: auto !important;
          margin: 0 0 0.5rem !important;
          overflow: visible !important;
          clip: auto !important;
          clip-path: none !important;
          white-space: normal !important;
          visibility: visible !important;
          opacity: 1 !important;
        }
      `}</style>
      <MajorFunctionalityPage config={lockConfig} visualState={visualState} onEvidenceCapture={onEvidenceCapture} />
    </>
  );
}

export default LockManagementScreen;

function LockOperationsSpotlight({ onEvidence }: { onEvidence: (actionId: string) => void }) {
  const [notice, setNotice] = useState('');

  function stageAction(actionId: string, message: string) {
    onEvidence(actionId);
    setNotice(message);
  }

  return (
    <section className="panel" aria-labelledby="lock-ops-spotlight-heading">
      <div className="panel-heading-row">
        <div>
            <p className="eyebrow">Lock operations</p>
          <h2 id="lock-ops-spotlight-heading">Lock lifecycle actions</h2>
        </div>
      </div>
      <p className="quote-intake-status">Preview data is visible for workflow review. Submission, extension, cancellation, and delivery actions are disabled until connected lock records are available.</p>
      <details className="field-help"><summary aria-label="Lock operation details">?</summary><span>Additional investor delivery and cutoff details are shown only when connected records are available.</span></details>
      <div className="offer-toolbar" aria-label="Local lock lifecycle actions">
        <button type="button" disabled onClick={() => stageAction('request-lock-review', 'Connected lock review is disabled until real lock records are available.')}>Start Lock Review (preview disabled)</button>
        <button type="button" disabled onClick={() => stageAction('extend-expiring-lock-review', 'Connected extension review is disabled until real lock records are available.')}>Stage Extension Review (preview disabled)</button>
        <button type="button" onClick={() => stageAction('show-expiry-items', 'Expiry items are ready for operations review.')}>Review Expiry Items</button>
      </div>
      {notice ? <div className="banner banner--info" role="status">{notice}</div> : null}
    </section>
  );
}
