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
  summary: 'Manages active locks, requests, expiring work, history, bulk actions, and investor delivery status.',
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
    { label: 'Evidence', value: 'Enabled', help: 'Interactions captured' },
  ],
  rows: [
    { id: 'lock-requested', borrowerRef: 'Borrower ref A', delivery: 'Not ready', status: 'requested' },
    { id: 'lock-confirmed', borrowerRef: 'Borrower ref B', delivery: 'Pending investor package', status: 'confirmed' },
    { id: 'lock-expired', borrowerRef: 'Borrower ref C', delivery: 'Needs review', status: 'expired' },
    { id: 'lock-cancelled', borrowerRef: 'Borrower ref D', delivery: 'Closed', status: 'cancelled' },
    { id: 'lock-delivered', borrowerRef: 'Borrower ref E', delivery: 'Acknowledged', status: 'delivered' },
  ],
  columns: [
    { key: 'borrowerRef', header: 'Borrower ref' },
    { key: 'delivery', header: 'Investor delivery' },
    { key: 'status', header: 'Status', render: (row) => <span className={`functionality-badge functionality-badge--${row.status}`}>{row.status}</span> },
  ],
  tableCaption: 'Lock management records',
  primaryActions: [{ id: 'request-lock-review', label: 'Stage Request Lock', variant: 'primary' }],
  secondaryActions: [{ id: 'extend-expiring-lock-review', label: 'Stage Extension Review' }, { id: 'extend-locks', label: 'Bulk extend' }, { id: 'cancel-locks', label: 'Bulk cancel', variant: 'danger' }, { id: 'deliver-locks', label: 'Bulk deliver' }],
  emptyMessage: 'No lock records are available for this workspace.',
  blockedMessage: 'Lock management is blocked until lock-service expiration and delivery references are available.',
  attentionMessage: 'Requested and expiring locks need operations review.',
  renderSpotlight: (onEvidence) => <LockOperationsSpotlight onEvidence={onEvidence} />,
};

export function LockManagementScreen({ visualState, onEvidenceCapture }: { visualState?: ScreenVisualState; onEvidenceCapture?: EvidenceCapture }) {
  return <MajorFunctionalityPage config={lockConfig} visualState={visualState} onEvidenceCapture={onEvidenceCapture} />;
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
      <details className="field-help"><summary aria-label="Lock operation details">?</summary><span>Additional investor delivery and cutoff details are shown only when connected records are available.</span></details>
      <div className="offer-toolbar" aria-label="Local lock lifecycle actions">
        <button type="button" onClick={() => stageAction('request-lock-review', 'Lock request review started. Complete required investor and disclosure checks before submitting.')}>Stage Request Lock</button>
        <button type="button" onClick={() => stageAction('extend-expiring-lock-review', 'Extension review started. Confirm investor cutoff and fee details before continuing.')}>Stage Extension Review</button>
        <button type="button" onClick={() => stageAction('show-expiry-items', 'Expiry items are ready for operations review.')}>Review Expiry Items</button>
      </div>
      {notice ? <div className="banner banner--info" role="status">{notice}</div> : null}
    </section>
  );
}
