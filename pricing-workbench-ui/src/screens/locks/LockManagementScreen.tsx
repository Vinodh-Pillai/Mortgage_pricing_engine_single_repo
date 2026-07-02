import { useEffect, useState } from 'react';
import { fetchLockManagement, requestLockManagementAction, type LockManagementAction, type LockManagementActionResult, type LockManagementRecord, type LockManagementView } from '../../lib/api/locks';
import { useOptionalTenantId } from '../../lib/data/tenant';
import type { EvidenceCapture } from '../shared/MajorFunctionalityPage';

type LockManagementState =
  | { kind: 'loading' }
  | { kind: 'loaded'; view: LockManagementView }
  | { kind: 'blocked'; message: string };

export const lockManagementEvidenceTarget = '.local-harness/evidence/PII-25-S04/lock-management.json';

type LockAction = LockManagementAction;

const lockActions: LockAction[] = ['read', 'detail', 'extend', 'relock', 'cancel', 'deliver'];
const bulkLockActions: LockAction[] = ['read', 'detail'];

export function LockManagementScreen({ onEvidenceCapture }: { onEvidenceCapture?: EvidenceCapture }) {
  const tenantId = useOptionalTenantId();
  const [state, setState] = useState<LockManagementState>(() => tenantId ? { kind: 'loading' } : { kind: 'blocked', message: 'Select a tenant context before loading lock management.' });
  const [actionResult, setActionResult] = useState<LockManagementActionResult | null>(null);
  const [selectedLockIds, setSelectedLockIds] = useState<Set<string>>(() => new Set());

  useEffect(() => {
    if (!tenantId) {
      setState({ kind: 'blocked', message: 'Select a tenant context before loading lock management.' });
      setSelectedLockIds(new Set());
      return undefined;
    }
    let active = true;
    setState({ kind: 'loading' });
    fetchLockManagement(tenantId)
      .then((view) => { if (active) { setSelectedLockIds(new Set()); setState({ kind: 'loaded', view }); } })
      .catch((error: unknown) => {
        const message = error instanceof Error ? error.message : 'Lock management backend is unavailable.';
        if (active) setState({ kind: 'blocked', message });
      });
    return () => { active = false; };
  }, [tenantId]);

  useEffect(() => {
    const refs = state.kind === 'loaded'
      ? [lockManagementEvidenceTarget, state.view.uiTraceId, ...state.view.locks.flatMap((lock) => [lock.lockId, lock.runId, ...lock.auditRefs])]
      : [lockManagementEvidenceTarget];
    onEvidenceCapture?.({
      screenId: 'lock-management',
      timestamp: new Date().toISOString(),
      state: state.kind === 'loaded' ? (state.view.locks.length > 0 ? 'ready' : 'empty') : state.kind === 'loading' ? 'loading' : 'blocked',
      dataRefs: refs,
      blockers: state.kind === 'blocked' ? [state.message] : state.kind === 'loaded' ? state.view.locks.flatMap((lock) => lock.blockers) : [],
      evidenceTarget: lockManagementEvidenceTarget,
      refs,
    });
  }, [onEvidenceCapture, state]);

  async function runAction(lock: LockManagementRecord, action: LockAction) {
    if (!tenantId) {
      setActionResult({ status: 'BLOCKED', message: 'Select a tenant context before submitting lock actions.', blockers: ['tenant-context-required'] });
      return;
    }
    if (!lock.availableActions.includes(action)) return;
    try {
      setActionResult(await requestLockManagementAction(tenantId, lock.lockId, action));
    } catch (error: unknown) {
      setActionResult({ status: 'BLOCKED', message: error instanceof Error ? error.message : `Lock ${action} action failed.`, blockers: ['lock-action-unavailable'] });
    }
  }

  function toggleLock(lockId: string) {
    setSelectedLockIds((current) => {
      const next = new Set(current);
      if (next.has(lockId)) next.delete(lockId);
      else next.add(lockId);
      return next;
    });
  }

  async function runBulkAction(action: LockAction) {
    if (!tenantId) {
      setActionResult({ status: 'BLOCKED', message: 'Select a tenant context before submitting bulk lock actions.', blockers: ['tenant-context-required'] });
      return;
    }
    if (state.kind !== 'loaded') return;
    if (selectedLockIds.size === 0) {
      setActionResult({ status: 'BLOCKED', message: `Select one or more locks before requesting bulk ${action}.`, blockers: ['lock-selection-required'] });
      return;
    }
    const selectedLocks = state.view.locks.filter((lock) => selectedLockIds.has(lock.lockId));
    const eligibleLocks = selectedLocks.filter((lock) => lock.availableActions.includes(action));
    if (eligibleLocks.length === 0) {
      setActionResult({ status: 'BLOCKED', message: `Selected locks do not advertise the ${action} action from lock-service.`, blockers: ['bulk-action-contract-unavailable'] });
      return;
    }
    try {
      const results = await Promise.all(eligibleLocks.map((lock) => requestLockManagementAction(tenantId, lock.lockId, action)));
      const blocked = results.filter((result) => result.status !== 'ACCEPTED');
      setActionResult({
        status: blocked.length === 0 ? 'ACCEPTED' : 'BLOCKED',
        message: blocked.length === 0 ? `Bulk ${action} recorded for ${eligibleLocks.length} lock${eligibleLocks.length === 1 ? '' : 's'}.` : `Bulk ${action} completed with ${blocked.length} blocked lock${blocked.length === 1 ? '' : 's'}.`,
        auditRef: results.map((result) => result.auditRef).filter(Boolean).join(', ') || null,
        blockers: blocked.flatMap((result) => result.blockers ?? []),
      });
    } catch (error: unknown) {
      setActionResult({ status: 'BLOCKED', message: error instanceof Error ? error.message : `Bulk ${action} action failed.`, blockers: ['bulk-lock-action-unavailable'] });
    }
  }

  if (state.kind === 'loading') {
    return <main className="functionality-page"><section className="panel"><h1>Lock Management</h1><p role="status">Loading lock-service records...</p></section></main>;
  }

  if (state.kind === 'blocked') {
    return (
      <main className="functionality-page" data-screen-id="lock-management">
        <section className="hero" aria-labelledby="lock-management-title">
          <p className="eyebrow">Lock service</p>
          <h1 id="lock-management-title">Lock Management</h1>
          <p>Review pending lock requests, expiring locks, investor delivery, and audit records only when lock-service supplies live records.</p>
        </section>
        <section className="panel" aria-labelledby="lock-management-blocked-heading">
          <h2 id="lock-management-blocked-heading">Live backend required</h2>
          <div className="banner banner--blocked" role="alert">
            <strong>Lock management blocked</strong>
            <span>{state.message}</span>
            <span>The UI is not rendering preview lock rows or disabled workflow copy as real lock management.</span>
          </div>
        </section>
      </main>
    );
  }

  const view = state.view;
  return (
    <main className="functionality-page" data-screen-id="lock-management" aria-labelledby="lock-management-title">
      <section className="hero" aria-labelledby="lock-management-title">
        <p className="eyebrow">Lock-service records</p>
        <h1 id="lock-management-title">Lock Management</h1>
        <p>Review live lock requests, expirations, investor delivery status, and audit evidence. Actions are enabled only when lock-service advertises the action for a record.</p>
      </section>

      <section className="page-metrics" aria-label="Lock management metrics">
        <div className="page-metric"><span>Workspace</span><strong>{view.tenantContext}</strong><small>{view.dependencyStatus}</small></div>
        <div className="page-metric"><span>Locks</span><strong>{String(view.locks.length)}</strong><small>lock-service supplied</small></div>
        <div className="page-metric"><span>Pending</span><strong>{String(view.pendingCount ?? view.locks.filter((lock) => isPendingStatus(lock.status)).length)}</strong><small>live status</small></div>
        <div className="page-metric"><span>Expiring</span><strong>{String(view.expiringCount ?? view.locks.filter((lock) => lock.expiryStatus === 'EXPIRING_SOON').length)}</strong><small>live expiration</small></div>
        <div className="page-metric"><span>Support ref</span><strong>{view.uiTraceId}</strong><small>backend trace</small></div>
      </section>

      {actionResult ? <ActionResult result={actionResult} /> : null}

      <section className="panel" aria-labelledby="bulk-lock-actions-heading">
        <h2 id="bulk-lock-actions-heading">Bulk lock actions</h2>
        <p role="status">{selectedLockIds.size} selected. Bulk read/detail actions call the lock-service detail contract for each selected row that advertises the action.</p>
        <div className="button-row" aria-label="Bulk lock actions">
          {bulkLockActions.map((action) => <button key={action} type="button" disabled={selectedLockIds.size === 0} onClick={() => void runBulkAction(action)}>Bulk {actionLabel(action)}</button>)}
        </div>
      </section>

      <section className="panel" aria-labelledby="lock-records-heading">
        <h2 id="lock-records-heading">Live lock records</h2>
        {view.locks.length === 0 ? <p role="status">No lock-service records are available for this workspace.</p> : (
          <div className="quote-table" role="table" aria-label="Lock management records">
            <div role="row" className="quote-table__row quote-table__row--head">
              <span role="columnheader">Select</span>
              <span role="columnheader">Lock</span>
              <span role="columnheader">Quote / borrower ref</span>
              <span role="columnheader">Status</span>
              <span role="columnheader">Expires</span>
              <span role="columnheader">Investor delivery</span>
              <span role="columnheader">Actions</span>
            </div>
            {view.locks.map((lock) => <LockRow key={lock.lockId} lock={lock} selected={selectedLockIds.has(lock.lockId)} onToggle={toggleLock} onAction={runAction} />)}
          </div>
        )}
      </section>

      <section className="panel" aria-labelledby="lock-audit-heading">
        <h2 id="lock-audit-heading">Investor delivery and audit evidence</h2>
        {view.locks.flatMap((lock) => lock.auditRefs).length === 0 ? <p role="status">No lock audit refs were supplied by lock-service.</p> : (
          <ul className="offer-list" aria-label="Lock audit refs">
            {view.locks.map((lock) => <li key={`${lock.lockId}-audit`}><strong>{lock.lockId}</strong><RefList values={lock.auditRefs} /></li>)}
          </ul>
        )}
      </section>
    </main>
  );
}

function LockRow({ lock, selected, onToggle, onAction }: { lock: LockManagementRecord; selected: boolean; onToggle: (lockId: string) => void; onAction: (lock: LockManagementRecord, action: LockAction) => void }) {
  return (
    <div role="row" className="quote-table__row">
      <span role="cell"><input type="checkbox" aria-label={`Select lock ${lock.lockId}`} checked={selected} onChange={() => onToggle(lock.lockId)} /></span>
      <span role="cell"><strong>{lock.lockId}</strong><br /><code>{lock.runId}</code></span>
      <span role="cell">{lock.borrowerRef}</span>
      <span role="cell">{lock.status}{lock.expiryStatus ? <><br /><small>{lock.expiryStatus}</small></> : null}{lock.blockers.length > 0 ? <RefList values={lock.blockers} /> : null}</span>
      <span role="cell">{lock.expiresAt ?? 'Not supplied'}</span>
      <span role="cell">{lock.investorDeliveryStatus}</span>
      <span role="cell"><div className="button-row" aria-label={`${lock.lockId} actions`}>{lockActions.map((action) => {
        const enabled = lock.availableActions.includes(action);
        const blocker = lock.actionBlockers?.[action];
        return <button key={action} type="button" disabled={!enabled} title={enabled ? undefined : blocker} aria-label={enabled ? actionLabel(action) : `${actionLabel(action)} disabled: ${blocker ?? 'not supported by lock-service contract'}`} onClick={() => onAction(lock, action)}>{enabled ? actionLabel(action) : `${actionLabel(action)} disabled`}</button>;
      })}</div></span>
    </div>
  );
}

function ActionResult({ result }: { result: LockManagementActionResult }) {
  const accepted = result.status === 'ACCEPTED';
  return (
    <div className={accepted ? 'banner banner--success' : 'banner banner--blocked'} role={accepted ? 'status' : 'alert'}>
      <strong>{accepted ? 'Lock action recorded' : 'Lock action blocked'}</strong>
      <span>{result.message}</span>
      {result.auditRef ? <code>{result.auditRef}</code> : null}
      <RefList values={result.blockers ?? []} />
    </div>
  );
}

function RefList({ values }: { values: string[] }) {
  if (values.length === 0) return null;
  return <ul>{values.map((value) => <li key={value}><code>{value}</code></li>)}</ul>;
}

function actionLabel(action: string) {
  return action[0].toUpperCase() + action.slice(1);
}

function isPendingStatus(status: string) {
  return ['REQUESTED', 'PENDING_APPROVAL', 'APPROVED', 'PENDING_INVESTOR_CONFIRMATION'].includes(status);
}

export default LockManagementScreen;
