import { useEffect, useState } from 'react';
import { confirmLockWorkflowAction, fetchLockWorkflow, type LockConfirmationRequest, type LockConfirmationResult, type LockWorkflowAction, type LockWorkflowStatus, type LockWorkflowView } from '../../lib/api/quoteRuns';
import type { ScreenProps } from '../contract/ScreenProps';
import { ConfirmLockDialog } from './ConfirmLockDialog';
import { DisclosuresPanel } from './DisclosuresPanel';
import { LockHistory } from './LockHistory';
import { LockStatusBanner } from './LockStatusBanner';
import { LockTermsPanel } from './LockTermsPanel';
import { PostLockActions } from './PostLockActions';
import { BlockedLock } from './BlockedLock';
import { valueText } from './lockWorkflowUtils';
export { countdownWarning, valueText } from './lockWorkflowUtils';

export type QuoteLockScreenProps = Partial<ScreenProps> & {
  workflow?: LockWorkflowView;
  confirmLock?: (request: LockConfirmationRequest) => Promise<LockConfirmationResult> | LockConfirmationResult;
  fetchImpl?: typeof fetch;
  onNavigate?: (path: string) => void;
};

export default function QuoteLockScreen({ tenantId = 'tenant-fixture', runId, optionId, uiTraceId = 'pii-24-s12-local-trace', workflow, confirmLock, fetchImpl, onEvidenceCapture, onNavigate }: QuoteLockScreenProps) {
  const [workflowState, setWorkflowState] = useState<LockWorkflowView>(() => workflow ?? createUnavailableLockWorkflow(tenantId, runId, optionId));
  const activeRunId = runId ?? workflowState.runId;
  const activeOfferId = optionId ?? workflowState.selectedOfferId;
  const [currentStatus, setCurrentStatus] = useState<LockWorkflowStatus>(workflowState.status);
  const [scrollComplete, setScrollComplete] = useState(false);
  const [checked, setChecked] = useState(false);
  const [signatureName, setSignatureName] = useState('');
  const [dialogOpen, setDialogOpen] = useState(false);
  const [confirmation, setConfirmation] = useState<LockConfirmationResult | null>(null);
  const [selectedAction, setSelectedAction] = useState<LockWorkflowAction | null>(null);
  const [isConfirming, setIsConfirming] = useState(false);
  const [actionFailure, setActionFailure] = useState<string | null>(null);
  const visualState = stateForLockWorkflow({ ...workflowState, status: currentStatus });
  const disclosuresAccepted = scrollComplete && checked && signatureName.trim().length > 0;
  const displayedHistory = confirmation?.historyEvent ? [...workflowState.history, confirmation.historyEvent] : workflowState.history;

  useEffect(() => {
    if (!workflow) return;
    setWorkflowState(workflow);
    setCurrentStatus(workflow.status);
    setConfirmation(null);
    setActionFailure(null);
  }, [workflow]);

  useEffect(() => {
    let cancelled = false;

    async function loadWorkflow() {
      try {
        const nextWorkflow = await fetchLockWorkflow(tenantId, activeRunId, activeOfferId, fetchImpl);
        if (!cancelled) {
          setWorkflowState(nextWorkflow);
          setCurrentStatus(nextWorkflow.status);
          setActionFailure(null);
        }
      } catch {
        if (!cancelled) setWorkflowState((current) => markWorkflowUnavailable(current));
      }
    }

    if (workflow && !fetchImpl) return () => { cancelled = true; };
    void loadWorkflow();
    if (currentStatus !== 'CONFIRMED') return () => { cancelled = true; };

    const poll = window.setInterval(() => void loadWorkflow(), 30000);
    return () => {
      cancelled = true;
      window.clearInterval(poll);
    };
  }, [activeOfferId, activeRunId, currentStatus, fetchImpl, tenantId, workflow]);

  useEffect(() => {
    onEvidenceCapture?.({
      screenId: 'quote-lock',
      timestamp: new Date().toISOString(),
      state: visualState,
      dataRefs: [tenantId, activeRunId, activeOfferId, workflowState.uiTraceId, uiTraceId, workflowState.terms.waterfallRef],
      blockers: workflowState.lockDisabled ? workflowState.blockers.map((blocker) => blocker.code) : [],
    });
  }, [activeOfferId, activeRunId, onEvidenceCapture, tenantId, uiTraceId, visualState, workflowState]);

  async function submitConfirmation(action: LockConfirmationRequest['action'] = 'confirm') {
    const request: LockConfirmationRequest = {
      selectedOfferId: activeOfferId,
      action,
      disclosuresAccepted,
      disclosureScrollComplete: scrollComplete,
      signatureName: signatureName.trim(),
      signedAt: new Date().toISOString(),
    };
    setIsConfirming(true);
    setActionFailure(null);
    try {
      const result = await (confirmLock?.(request) ?? confirmLockWorkflowAction(tenantId, activeRunId, request, fetchImpl));
      setConfirmation(result);
      if (result.status === 'CONFIRMED') setCurrentStatus(action === 'confirm' ? 'CONFIRMED' : action === 'float_down' ? 'FLOAT_DOWN' : action === 'relock' ? 'RELOCKED' : 'EXTENDED');
      setDialogOpen(false);
      setSelectedAction(null);
    } catch {
      setActionFailure(lockActionUnavailableMessage(action, currentStatus));
    } finally {
      setIsConfirming(false);
    }
  }

  function navigate(path: string) {
    onNavigate?.(path);
  }

  if (workflowState.lockDisabled || currentStatus === 'BLOCKED') {
    return <BlockedLock workflow={workflowState} onReturn={() => navigate(`/quote/${encodeURIComponent(activeRunId)}/offers`)} />;
  }

  return (
    <main className="quote-lock-screen" aria-labelledby="quote-lock-title">
      <section className="hero" aria-labelledby="quote-lock-title">
        <p className="eyebrow">Lock workflow | PII-24-S12</p>
        <h1 id="quote-lock-title">Lock Workflow</h1>
        <p>Review backend supplied terms, complete disclosures, confirm lock, and manage post-lock actions without local pricing decisions.</p>
        <button type="button" className="button-secondary" onClick={() => navigate(`/quote/${encodeURIComponent(activeRunId)}/offers`)}>Back to offers</button>
      </section>

      <LockStatusBanner status={currentStatus} lockId={confirmation?.lockId ?? workflowState.lockId} expiresAt={confirmation?.expiresAt ?? workflowState.terms.expiresAt} extensionCount={displayedHistory.filter((event) => event.eventType === 'extended').length} />
      {actionFailure ? <div className="banner banner--warning" role="alert">{actionFailure}</div> : null}
      {confirmation?.message ? <div className="banner banner--info" role="status">{confirmation.message}</div> : null}

      <section className="quote-detail-layout" aria-label="Responsive lock workflow layout" style={{ display: 'grid', gap: '1rem', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 22rem), 1fr))' }}>
        <div>
          <LockTermsPanel workflow={workflowState} onLock={() => setDialogOpen(true)} disabled={!disclosuresAccepted || isConfirming} />
          <DisclosuresPanel disclosures={workflowState.disclosures} scrollComplete={scrollComplete} checked={checked} signatureName={signatureName} onScrollComplete={setScrollComplete} onCheckedChange={setChecked} onSignatureChange={setSignatureName} />
        </div>
        <div>
          <PostLockActions status={currentStatus} actions={workflowState.postLockActions} onSelect={(action) => setSelectedAction(action)} />
          <LockHistory events={displayedHistory} />
        </div>
      </section>

      <ConfirmLockDialog open={dialogOpen} workflow={workflowState} disclosuresAccepted={disclosuresAccepted} confirmation={confirmation} confirming={isConfirming} onCancel={() => setDialogOpen(false)} onConfirm={() => void submitConfirmation('confirm')} />

      {selectedAction ? (
        <section role="dialog" aria-modal="true" aria-labelledby="post-lock-action-heading" className="panel">
          <h2 id="post-lock-action-heading">{selectedAction.label}</h2>
          <p>{selectedAction.terms}</p>
          <dl className="status-grid">
            <dt>Fee</dt><dd>{valueText(selectedAction.fee)}</dd>
            <dt>Max days</dt><dd>{valueText(selectedAction.maxDays)}</dd>
            <dt>Approval required</dt><dd>{selectedAction.approvalRequired ? 'Yes' : 'No'}</dd>
          </dl>
          <button type="button" disabled={!selectedAction.eligible || isConfirming} onClick={() => void submitConfirmation(selectedAction.action)}>Confirm {selectedAction.label}</button>
          <button type="button" className="button-secondary" onClick={() => setSelectedAction(null)}>Cancel</button>
        </section>
      ) : null}
    </main>
  );
}

function lockActionUnavailableMessage(action: LockConfirmationRequest['action'], currentStatus: LockWorkflowStatus) {
  const actionLabel = action === 'confirm' ? 'lock confirmation' : action === 'float_down' ? 'float-down request' : action === 'relock' ? 'relock request' : 'lock extension request';
  return `Lock-service ${actionLabel} is blocked or unavailable. Current lock status remains ${currentStatus}. No synthetic success was applied.`;
}

function createUnavailableLockWorkflow(tenantId: string, runId?: string, optionId?: string): LockWorkflowView {
  return {
    tenantContext: tenantId,
    runId: runId ?? 'run-unavailable',
    selectedOfferId: optionId ?? 'offer-unavailable',
    status: 'BLOCKED',
    lockIdPreview: 'lock-service-unavailable',
    lockId: null,
    terms: {
      productLabel: 'Unavailable',
      investor: 'Unavailable',
      channel: 'Unavailable',
      noteRate: 'Unavailable',
      finalPriceBps: 'Unavailable',
      lockPeriodDays: 0,
      expiresAt: new Date(0).toISOString(),
      waterfallRef: 'lock-service:unavailable',
      adjustmentRefs: [],
      marginRefs: [],
      investorConfirmationRequired: true,
    },
    disclosures: [],
    lockDisabled: true,
    lockDisabledReason: 'Backend lock workflow is unavailable.',
    blockers: [lockServiceUnavailableBlocker()],
    postLockActions: [],
    history: [],
    uiTraceId: 'lock-service-unavailable',
    events: ['lock.workflow.unavailable'],
    fallbackReason: 'Backend lock workflow is unavailable.',
  };
}

function markWorkflowUnavailable(workflow: LockWorkflowView): LockWorkflowView {
  return {
    ...workflow,
    status: 'BLOCKED',
    lockDisabled: true,
    lockDisabledReason: 'Backend lock workflow is unavailable.',
    blockers: workflow.blockers.length ? workflow.blockers : [lockServiceUnavailableBlocker()],
    events: [...workflow.events, 'lock.workflow.unavailable'],
    fallbackReason: 'Backend lock workflow is unavailable.',
  };
}

function lockServiceUnavailableBlocker() {
  return {
    code: 'LOCK_SERVICE_UNAVAILABLE',
    message: 'The lock-service did not return durable lock workflow evidence.',
    remediation: 'Keep the current quote lock status unchanged and retry after lock-service is available.',
    sourceRef: 'lock-service:unavailable',
  };
}

export function stateForLockWorkflow(workflow: LockWorkflowView) {
  if (workflow.lockDisabled || workflow.status === 'BLOCKED') return 'blocked';
  if (!workflow.terms || workflow.disclosures.length === 0) return 'empty';
  if (workflow.status === 'READY') return 'ready';
  return 'needs-attention';
}
