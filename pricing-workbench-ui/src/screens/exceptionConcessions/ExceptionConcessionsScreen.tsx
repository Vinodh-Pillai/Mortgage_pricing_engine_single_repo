import { useEffect, useState } from 'react';
import { DiagnosticsDetails } from '../../components/DiagnosticsDetails';
import {
  fetchExceptionConcessionWorkbench,
  type AuthorityMatrixEntryView,
  type ConcessionRequestView,
  type EligibilityExceptionView,
  type ExceptionConcessionSection,
  type ExceptionConcessionWorkbenchView,
  type HistoryReplayExportView,
  type PriceGuardAttemptView,
  type RiskEventView,
} from '../../lib/api/exceptionConcessions';

type ExceptionConcessionsState =
  | { kind: 'loading' }
  | { kind: 'loaded'; view: ExceptionConcessionWorkbenchView }
  | { kind: 'unreachable'; message: string };

type ExceptionConcessionsScreenProps = {
  tenantContext?: string;
  evidence?: ExceptionConcessionWorkbenchView;
  fetchImpl?: typeof fetch;
  onEvidenceCapture?: (evidence: { screenId: string; state: string; evidenceTarget: string; refs: string[] }) => void;
  onNavigate?: (target: string) => void;
};

type TabId = 'requests' | 'eligibility' | 'authority' | 'price-guard' | 'risk' | 'history';

const evidenceTarget = '.local-harness/evidence/PII-24-S17/exception-concessions.json';
const defaultTenantContext = 'ui-preview-tenant';
const tabs: { id: TabId; label: string }[] = [
  { id: 'requests', label: 'Requests' },
  { id: 'eligibility', label: 'Eligibility Exceptions' },
  { id: 'authority', label: 'Authority Matrix' },
  { id: 'price-guard', label: 'Price Guard' },
  { id: 'risk', label: 'Risk Events' },
  { id: 'history', label: 'History' },
];

export default function ExceptionConcessionsScreen({
  tenantContext = defaultTenantContext,
  evidence,
  fetchImpl,
  onEvidenceCapture,
  onNavigate,
}: ExceptionConcessionsScreenProps) {
  const [exceptionState, setExceptionState] = useState<ExceptionConcessionsState>(() => evidence ? { kind: 'loaded', view: evidence } : { kind: 'loading' });
  const [activeTab, setActiveTab] = useState<TabId>('requests');
  const [statusFilter, setStatusFilter] = useState('all');
  const [typeFilter, setTypeFilter] = useState('all');
  const [exported, setExported] = useState('');

  useEffect(() => {
    if (evidence) {
      setExceptionState({ kind: 'loaded', view: evidence });
      return;
    }

    let active = true;
    fetchExceptionConcessionWorkbench(tenantContext, fetchImpl)
      .then((view) => {
        if (active) setExceptionState({ kind: 'loaded', view });
      })
      .catch((error: unknown) => {
        const message = error instanceof Error ? error.message : 'Exception concession workbench is unavailable.';
        if (active) setExceptionState({ kind: 'unreachable', message });
      });
    return () => {
      active = false;
    };
  }, [evidence, fetchImpl, tenantContext]);

  useEffect(() => {
    if (exceptionState.kind !== 'loaded') return;
    onEvidenceCapture?.({
      screenId: 'exception-concessions',
      state: stateForExceptionConcessions(exceptionState.view),
      evidenceTarget,
      refs: collectEvidenceRefs(exceptionState.view),
    });
  }, [exceptionState, onEvidenceCapture]);

  if (exceptionState.kind === 'loading') {
    return <section className="panel" aria-labelledby="exception-concession-heading"><h2 id="exception-concession-heading">Exception Concessions</h2><p role="status">Loading exception concessions workbench...</p></section>;
  }

  if (exceptionState.kind === 'unreachable') {
    return (
      <section className="panel" aria-labelledby="exception-concession-heading">
        <h2 id="exception-concession-heading">Exception Concessions</h2>
        <div className="banner banner--blocked" role="alert">
          <strong>Exception concessions unavailable</strong>
          <span>{exceptionState.message}</span>
          <span>Connected exception-service evidence is required; the UI will not synthesize exception policy or authority decisions.</span>
        </div>
      </section>
    );
  }

  const view = exceptionState.view;
  const requests = view.concessionRequests ?? [];
  const filteredRequests = requests.filter((request) => {
    if (statusFilter !== 'all' && request.status !== statusFilter) return false;
    if (typeFilter !== 'all' && request.type !== typeFilter) return false;
    return true;
  });

  return (
    <>
      <section className="hero hero--admin" aria-labelledby="exception-concession-title">
        <p className="eyebrow">Exception service - PII-24-S17</p>
        <h2 id="exception-concession-title">Exception Concessions</h2>
        <p>
          Manage concession requests, eligibility exceptions, authority evidence, manual price guard decisions, risk events,
          and history exports from backend-owned references. The browser displays configured evidence only and does not infer pricing policy.
        </p>
      </section>

      <section className="panel exception-concessions-sticky" aria-labelledby="exception-concession-heading">
        <div className="panel-heading-row">
          <div>
            <p className="eyebrow">Tenant context</p>
            <h2 id="exception-concession-heading">Exception concessions workspace</h2>
          </div>
          <button type="button" disabled aria-describedby="new-request-disabled">New Request</button>
        </div>
        <p id="new-request-disabled" className="field-help">New requests stay disabled until exception-service supplies a write contract and authority evidence.</p>
        <dl className="status-grid">
          <dt>Tenant</dt><dd>{displayText(view.tenantContext || tenantContext)}</dd>
          <dt>Status</dt><dd>{displayText(view.status)}</dd>
          <dt>Support reference</dt><dd><code>{view.uiTraceId}</code></dd>
          <dt>Replay hash</dt><dd><code>{view.replayHash}</code></dd>
          <dt>Export package</dt><dd><code>{view.exportManifestRef}</code></dd>
          <dt>Evidence target</dt><dd><code>{evidenceTarget}</code></dd>
        </dl>
        <div className={stateForExceptionConcessions(view) === 'blocked' ? 'banner banner--blocked' : 'banner banner--info'} role={stateForExceptionConcessions(view) === 'blocked' ? 'alert' : 'status'}>
          <strong>{displayText(view.status)}</strong>
          <span>{displayText(view.dependencyStatus)}</span>
          <span>{displayText(view.fallbackReason)}</span>
        </div>
        <DiagnosticsDetails items={[`Support reference: ${view.uiTraceId}`, `Processing record: ${view.replayHash}`, `Export package: ${view.exportManifestRef}`]} />
        <nav className="custom-rules-shell__nav" aria-label="Exception concessions tabs">
          {tabs.map((tab) => <button key={tab.id} type="button" aria-pressed={activeTab === tab.id} onClick={() => setActiveTab(tab.id)}>{tab.label}</button>)}
        </nav>
        {exported ? <textarea aria-label="Exported exception concessions evidence" readOnly value={exported} /> : null}
      </section>

      {activeTab === 'requests' ? <RequestsPanel requests={filteredRequests} allRequests={requests} statusFilter={statusFilter} typeFilter={typeFilter} onStatusFilter={setStatusFilter} onTypeFilter={setTypeFilter} onNavigate={onNavigate} /> : null}
      {activeTab === 'eligibility' ? <EligibilityPanel exceptions={view.eligibilityExceptions ?? []} /> : null}
      {activeTab === 'authority' ? <AuthorityMatrixPanel entries={view.authorityMatrix ?? []} /> : null}
      {activeTab === 'price-guard' ? <PriceGuardPanel guard={view.manualPriceMutationGuard} attempts={view.priceGuardAttempts ?? []} onNavigate={onNavigate} /> : null}
      {activeTab === 'risk' ? <RiskEventsPanel events={view.riskEvents ?? []} /> : null}
      {activeTab === 'history' ? <HistoryPanel rows={view.historyReplayExports ?? []} view={view} onExport={() => setExported(exportExceptionConcessionsJson(view))} /> : null}

      <section className="panel" aria-labelledby="exception-cross-service-heading">
        <h2 id="exception-cross-service-heading">Cross-service references and blockers</h2>
        <ExpandableRefList label="Cross-service refs" values={view.crossServiceRefs} emptyText="No cross-service refs supplied." open />
        <ExpandableRefList label="Version refs" values={view.versionRefs} emptyText="No version refs supplied." />
        <ExpandableRefList label="Audit refs" values={view.auditRefs} emptyText="No audit refs supplied." open />
        <ExpandableRefList label="Workbench blockers" values={view.blockers} emptyText="No blockers supplied." open />
        <ExpandableRefList label="Workbench events" values={view.events} emptyText="No events supplied." />
      </section>

      <section className="panel" aria-labelledby="exception-section-heading">
        <h2 id="exception-section-heading">Configured section coverage</h2>
        <div className="offer-list" role="list" aria-label="Exception concession workbench sections">
          {view.sections.map((section) => <ExceptionConcessionSectionCard key={section.sectionId} section={section} />)}
        </div>
      </section>
    </>
  );
}

export function stateForExceptionConcessions(view: ExceptionConcessionWorkbenchView) {
  if (view.status.toLowerCase().includes('blocked') || view.blockers.length > 0) return 'blocked';
  if (!view.concessionRequests?.length && !view.eligibilityExceptions?.length && !view.authorityMatrix?.length && !view.riskEvents?.length && !view.historyReplayExports?.length) return 'needs-attention';
  return 'ready';
}

export function exportExceptionConcessionsJson(view: ExceptionConcessionWorkbenchView) {
  return JSON.stringify({ evidenceTarget, exportedAt: 'local-preview', view }, null, 2);
}

function RequestsPanel({ requests, allRequests, statusFilter, typeFilter, onStatusFilter, onTypeFilter, onNavigate }: { requests: ConcessionRequestView[]; allRequests: ConcessionRequestView[]; statusFilter: string; typeFilter: string; onStatusFilter: (value: string) => void; onTypeFilter: (value: string) => void; onNavigate?: (target: string) => void }) {
  const statuses = uniqueValues(allRequests.map((request) => request.status));
  const types = uniqueValues(allRequests.map((request) => request.type));
  return (
    <section className="panel" aria-labelledby="requests-heading">
      <div className="panel-heading-row"><div><p className="eyebrow">Requests</p><h2 id="requests-heading">Concession request, review, approval, and audit</h2></div></div>
      <div className="intake-form" role="group" aria-label="Concession request filters">
        <label>Status<select value={statusFilter} onChange={(event) => onStatusFilter(event.target.value)}><option value="all">All statuses</option>{statuses.map((status) => <option key={status} value={status}>{displayText(status)}</option>)}</select></label>
        <label>Type<select value={typeFilter} onChange={(event) => onTypeFilter(event.target.value)}><option value="all">All types</option>{types.map((type) => <option key={type} value={type}>{displayText(type)}</option>)}</select></label>
      </div>
      <div className="quote-table" role="table" aria-label="Concession requests table">
        <div role="row" className="quote-table__row quote-table__row--head"><span role="columnheader">Request ID</span><span role="columnheader">Type</span><span role="columnheader">Status</span><span role="columnheader">Requestor</span><span role="columnheader">Quote Ref</span><span role="columnheader">Amount/Impact</span><span role="columnheader">Created</span><span role="columnheader">Assigned Approver</span><span role="columnheader">SLA</span><span role="columnheader">Actions</span></div>
        {requests.length === 0 ? <div role="row" className="quote-table__row"><span role="cell">No concession requests match the current filters.</span></div> : requests.map((request) => <ConcessionRequestRow key={request.requestId} request={request} onNavigate={onNavigate} />)}
      </div>
      <div className="banner banner--info" role="status">New request modal is blocked until exception-service supplies type, supporting document, and write-action contracts.</div>
    </section>
  );
}

function ConcessionRequestRow({ request, onNavigate }: { request: ConcessionRequestView; onNavigate?: (target: string) => void }) {
  return (
    <div role="row" className="quote-table__row">
      <span role="cell"><code>{request.requestId}</code></span>
      <span role="cell">{displayText(request.type)}</span>
      <span role="cell">{displayText(request.status)}</span>
      <span role="cell"><code>{request.requestorRef}</code></span>
      <span role="cell"><code>{request.quoteRef}</code></span>
      <span role="cell"><code>{request.impactRef}</code></span>
      <span role="cell"><code>{request.createdRef}</code></span>
      <span role="cell"><code>{request.assignedApproverRef}</code></span>
      <span role="cell"><code>{request.slaRef}</code></span>
      <span role="cell"><ActionButtons actions={request.allowedActions} disabledReason="Backend action contract required" onNavigate={request.quoteRef ? () => onNavigate?.(`/quote/preview/offers?exception=${encodeURIComponent(request.requestId)}`) : undefined} /><ExpandableRefList label={`${request.requestId} audit refs`} values={request.auditRefs} emptyText="No audit refs supplied." /></span>
    </div>
  );
}

function EligibilityPanel({ exceptions }: { exceptions: EligibilityExceptionView[] }) {
  return (
    <section className="panel" aria-labelledby="eligibility-exceptions-heading">
      <h2 id="eligibility-exceptions-heading">Eligibility exceptions and renewal evidence</h2>
      <div className="quote-table" role="table" aria-label="Eligibility exceptions table">
        <div role="row" className="quote-table__row quote-table__row--head"><span role="columnheader">Exception ID</span><span role="columnheader">Rule Code</span><span role="columnheader">Quote Ref</span><span role="columnheader">Borrower Fact</span><span role="columnheader">Standard Value</span><span role="columnheader">Exception Value</span><span role="columnheader">Override</span><span role="columnheader">Status</span><span role="columnheader">Approver</span><span role="columnheader">Expiration/Renewal</span></div>
        {exceptions.length === 0 ? <div role="row" className="quote-table__row"><span role="cell">No eligibility exceptions supplied by exception-service.</span></div> : exceptions.map((exception) => <EligibilityExceptionRow key={exception.exceptionId} exception={exception} />)}
      </div>
      <div className="banner banner--info" role="status">Create exception remains disabled until backend rule-picker and renewal contracts are configured.</div>
    </section>
  );
}

function EligibilityExceptionRow({ exception }: { exception: EligibilityExceptionView }) {
  return (
    <div role="row" className="quote-table__row">
      <span role="cell"><code>{exception.exceptionId}</code></span><span role="cell"><code>{exception.ruleCodeRef}</code></span><span role="cell"><code>{exception.quoteRef}</code></span><span role="cell"><code>{exception.borrowerFactRef}</code></span><span role="cell"><code>{exception.standardValueRef}</code></span><span role="cell"><code>{exception.exceptionValueRef}</code></span><span role="cell">{displayText(exception.overrideType)}</span><span role="cell">{displayText(exception.status)}</span><span role="cell"><code>{exception.approverRef}</code></span><span role="cell"><code>{exception.expirationRef}</code><br /><code>{exception.renewalWorkflowRef}</code><ExpandableRefList label={`${exception.exceptionId} audit refs`} values={exception.auditRefs} emptyText="No audit refs supplied." /></span>
    </div>
  );
}

function AuthorityMatrixPanel({ entries }: { entries: AuthorityMatrixEntryView[] }) {
  return (
    <section className="panel" aria-labelledby="authority-matrix-heading">
      <h2 id="authority-matrix-heading">Authority matrix refs and escalation paths</h2>
      <p className="field-help">Limits and roles are displayed only as connected governance refs; no browser authority matrix logic is evaluated.</p>
      <div className="quote-table" role="table" aria-label="Authority matrix table">
        <div role="row" className="quote-table__row quote-table__row--head"><span role="columnheader">Role</span><span role="columnheader">Exception Type</span><span role="columnheader">Limit Ref</span><span role="columnheader">Escalation Path</span><span role="columnheader">Governance Config</span><span role="columnheader">Audit Refs</span></div>
        {entries.length === 0 ? <div role="row" className="quote-table__row"><span role="cell">No authority matrix entries supplied by governance-service.</span></div> : entries.map((entry) => <div key={`${entry.roleRef}-${entry.exceptionTypeRef}`} role="row" className="quote-table__row"><span role="cell"><code>{entry.roleRef}</code></span><span role="cell"><code>{entry.exceptionTypeRef}</code></span><span role="cell"><code>{entry.limitRef}</code></span><span role="cell"><code>{entry.escalationPathRef}</code></span><span role="cell"><code>{entry.governanceConfigRef}</code><br /><a href="/admin/governance">Open governance config</a></span><span role="cell"><ExpandableRefList label={`${entry.roleRef} audit refs`} values={entry.auditRefs} emptyText="No audit refs supplied." /></span></div>)}
      </div>
    </section>
  );
}

function PriceGuardPanel({ guard, attempts, onNavigate }: { guard: ExceptionConcessionWorkbenchView['manualPriceMutationGuard']; attempts: PriceGuardAttemptView[]; onNavigate?: (target: string) => void }) {
  return (
    <section className="panel" aria-labelledby="manual-mutation-guard-heading">
      <h2 id="manual-mutation-guard-heading">Manual price edit guard</h2>
      <div className="banner banner--blocked" role="alert">
        <strong>{displayText(guard.decision)}</strong><span>Commit disabled: {guard.commitDisabled ? 'yes' : 'no'}</span><span>Escalation path: {displayText(guard.escalationPath)}</span>
      </div>
      <button type="button" disabled={guard.commitDisabled} aria-describedby="manual-mutation-guard-reasons">Commit manual price mutation</button>
      <button type="button" className="button-secondary" onClick={() => onNavigate?.('/exceptions/concessions?prefill=manual-price-guard')}>Request Override</button>
      <div id="manual-mutation-guard-reasons"><ExpandableRefList label="Backend denial reason codes" values={guard.reasonCodes} emptyText="No reason codes supplied." open /><ExpandableRefList label="Guard audit and replay refs" values={[guard.auditRef, guard.replayHash].filter(Boolean)} emptyText="No guard refs supplied." open /></div>
      <div className="quote-table" role="table" aria-label="Blocked manual price edit attempts"><div role="row" className="quote-table__row quote-table__row--head"><span role="columnheader">Attempt</span><span role="columnheader">Quote Ref</span><span role="columnheader">Decision</span><span role="columnheader">Reasons</span><span role="columnheader">Audit/Replay</span></div>{attempts.length === 0 ? <div role="row" className="quote-table__row"><span role="cell">No blocked manual price edit attempts supplied.</span></div> : attempts.map((attempt) => <div key={attempt.attemptId} role="row" className="quote-table__row"><span role="cell"><code>{attempt.attemptId}</code></span><span role="cell"><code>{attempt.quoteRef}</code></span><span role="cell">{displayText(attempt.decision)}</span><span role="cell"><ExpandableRefList label={`${attempt.attemptId} reason codes`} values={attempt.reasonCodes} emptyText="No reason codes supplied." /></span><span role="cell"><code>{attempt.auditRef}</code><br /><code>{attempt.replayHash}</code></span></div>)}</div>
    </section>
  );
}

function RiskEventsPanel({ events }: { events: RiskEventView[] }) {
  return (
    <section className="panel" aria-labelledby="risk-events-heading"><h2 id="risk-events-heading">Risk events timeline and alert refs</h2><p className="field-help">Detection rules and alert channels are rendered as configured refs only; the UI does not evaluate thresholds.</p><div className="quote-table" role="table" aria-label="Risk events timeline"><div role="row" className="quote-table__row quote-table__row--head"><span role="columnheader">Event ID</span><span role="columnheader">Type</span><span role="columnheader">Severity</span><span role="columnheader">Quote Ref</span><span role="columnheader">Detected At</span><span role="columnheader">Status</span><span role="columnheader">Owner</span><span role="columnheader">Actions and Alerts</span></div>{events.length === 0 ? <div role="row" className="quote-table__row"><span role="cell">No risk events supplied by compliance-service.</span></div> : events.map((event) => <div key={event.eventId} role="row" className="quote-table__row"><span role="cell"><code>{event.eventId}</code></span><span role="cell">{displayText(event.type)}</span><span role="cell">{displayText(event.severity)}</span><span role="cell"><code>{event.quoteRef}</code></span><span role="cell"><code>{event.detectedAtRef}</code></span><span role="cell">{displayText(event.status)}</span><span role="cell"><code>{event.ownerRef}</code></span><span role="cell"><ActionButtons actions={event.actionRefs} disabledReason="Risk action contract required" /><ExpandableRefList label={`${event.eventId} alert refs`} values={event.alertRefs} emptyText="No alert refs supplied." /><ExpandableRefList label={`${event.eventId} audit refs`} values={event.auditRefs} emptyText="No audit refs supplied." /></span></div>)}</div></section>
  );
}

function HistoryPanel({ rows, view, onExport }: { rows: HistoryReplayExportView[]; view: ExceptionConcessionWorkbenchView; onExport: () => void }) {
  return (
    <section className="panel" aria-labelledby="history-replay-heading"><div className="panel-heading-row"><div><p className="eyebrow">History</p><h2 id="history-replay-heading">History, replay, export, retention, and legal hold</h2></div><button type="button" disabled={view.blockers.length > 0} onClick={onExport}>Export evidence JSON</button></div><p className="field-help">CSV, PDF, and JSON export remain governed by backend export refs and legal-hold state.</p><div className="quote-table" role="table" aria-label="History replay export table"><div role="row" className="quote-table__row quote-table__row--head"><span role="columnheader">Event</span><span role="columnheader">Type</span><span role="columnheader">Status</span><span role="columnheader">Actor</span><span role="columnheader">Replay</span><span role="columnheader">Export</span><span role="columnheader">Retention/Legal Hold</span></div>{rows.length === 0 ? <div role="row" className="quote-table__row"><span role="cell">No history or replay rows supplied.</span></div> : rows.map((row) => <div key={row.eventId} role="row" className="quote-table__row"><span role="cell"><code>{row.eventId}</code></span><span role="cell">{displayText(row.type)}</span><span role="cell">{displayText(row.status)}</span><span role="cell"><code>{row.actorRef}</code></span><span role="cell"><code>{row.replayHash}</code></span><span role="cell"><code>{row.exportRef}</code></span><span role="cell"><code>{row.retentionClassRef}</code><br /><code>{row.legalHoldRef}</code><ExpandableRefList label={`${row.eventId} audit refs`} values={row.auditRefs} emptyText="No audit refs supplied." /></span></div>)}</div></section>
  );
}

function ExceptionConcessionSectionCard({ section }: { section: ExceptionConcessionSection }) {
  return <article className="offer-card" role="listitem" aria-label={`${section.label} section`}><div className="offer-card__header"><div><h3>{displayText(section.label)}</h3><p>{displayText(section.sectionId)}</p></div><strong>{displayText(section.status)}</strong></div><p>{displayText(section.summary)}</p><ExpandableRefList label={`${section.label} backend refs`} values={section.backendRefs} emptyText="No backend refs supplied." /><ExpandableRefList label={`${section.label} audit refs`} values={section.auditRefs} emptyText="No audit refs supplied." /></article>;
}

function ActionButtons({ actions, disabledReason, onNavigate }: { actions: string[]; disabledReason: string; onNavigate?: () => void }) {
  const safeActions = actions.length > 0 ? actions : ['View'];
  return <div className="button-row" role="group" aria-label="Disabled action affordances">{safeActions.map((action) => <button key={action} type="button" disabled={action !== 'View'} onClick={action === 'View' ? onNavigate : undefined}>{displayText(action)}</button>)}<p className="field-help">{disabledReason}</p></div>;
}

function ExpandableRefList({ label, values, emptyText, open = false }: { label: string; values: string[]; emptyText: string; open?: boolean }) {
  return <details className="blocker-details" open={open}><summary>{label}</summary>{values.length === 0 ? <p>{emptyText}</p> : <ul>{values.map((value) => <li key={value}><code>{value}</code></li>)}</ul>}</details>;
}

function collectEvidenceRefs(view: ExceptionConcessionWorkbenchView) {
  return [view.replayHash, view.exportManifestRef, ...view.crossServiceRefs, ...view.versionRefs, ...view.auditRefs, ...view.sections.flatMap((section) => [...section.backendRefs, ...section.auditRefs]), ...(view.concessionRequests ?? []).flatMap((request) => [request.requestId, request.quoteRef, request.impactRef, ...request.auditRefs]), ...(view.eligibilityExceptions ?? []).flatMap((exception) => [exception.exceptionId, exception.ruleCodeRef, exception.borrowerFactRef, exception.standardValueRef, exception.exceptionValueRef, ...exception.auditRefs]), ...(view.authorityMatrix ?? []).flatMap((entry) => [entry.roleRef, entry.exceptionTypeRef, entry.limitRef, entry.escalationPathRef, entry.governanceConfigRef, ...entry.auditRefs]), view.manualPriceMutationGuard.auditRef, view.manualPriceMutationGuard.replayHash, ...(view.priceGuardAttempts ?? []).flatMap((attempt) => [attempt.attemptId, attempt.quoteRef, attempt.auditRef, attempt.replayHash]), ...(view.riskEvents ?? []).flatMap((event) => [event.eventId, event.quoteRef, event.detectedAtRef, event.ownerRef, ...event.alertRefs, ...event.actionRefs, ...event.auditRefs]), ...(view.historyReplayExports ?? []).flatMap((row) => [row.eventId, row.actorRef, row.replayHash, row.exportRef, row.retentionClassRef, row.legalHoldRef, ...row.auditRefs])].filter(Boolean);
}

function displayText(value: string | number | boolean | null | undefined) {
  if (value === null || value === undefined || value === '') return 'Not supplied';
  return String(value).replace(/[_-]+/g, ' ');
}

function uniqueValues(values: string[]) {
  return Array.from(new Set(values.filter(Boolean))).sort((a, b) => a.localeCompare(b));
}
