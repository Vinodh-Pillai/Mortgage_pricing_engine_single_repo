import { useEffect, useMemo, useState } from 'react';
import { DiagnosticsDetails } from '../../components/DiagnosticsDetails';
import {
  addOpsCaseNote,
  assignOpsCase,
  fetchOpsCaseDetail,
  fetchOpsCases,
  updateOpsCaseStatus,
  type OpsCaseActionResult,
  type OpsCaseChart,
  type OpsCaseDetail,
  type OpsCaseListView,
  type OpsCaseMetric,
  type OpsCaseQueueSummary,
  type OpsCaseSummary,
  type OpsEscalationSummary,
} from '../../lib/api/opsCases';

type OpsCasesState =
  | { kind: 'loading' }
  | { kind: 'loaded'; view: OpsCaseListView }
  | { kind: 'unreachable'; message: string };

type OpsCaseDetailState =
  | { kind: 'idle' }
  | { kind: 'loading' }
  | { kind: 'loaded'; detail: OpsCaseDetail }
  | { kind: 'unreachable'; message: string };

type OpsCasesScreenProps = {
  tenantContext?: string;
  evidence?: OpsCaseListView;
  detailEvidence?: OpsCaseDetail;
  fetchImpl?: typeof fetch;
  initialPathname?: string;
  onEvidenceCapture?: (evidence: { screenId: string; state: string; evidenceTarget: string; refs: string[] }) => void;
  onNavigate?: (target: string) => void;
};

type TabId = 'dashboard' | 'queues' | 'cases' | 'escalations';
type CaseFilters = { queue: string; priority: string; slaState: string; owner: string; status: string; sort: string };

const evidenceTarget = '.local-harness/evidence/PII-24-S19/ops-cases.json';
const defaultTenantContext = 'ui-preview-tenant';
const defaultFilters: CaseFilters = { queue: 'all', priority: 'all', slaState: 'all', owner: 'all', status: 'all', sort: 'age' };
const tabRoutes: Record<TabId, string> = { dashboard: '/ops/dashboard', queues: '/ops/queues', cases: '/ops/cases', escalations: '/ops/escalations' };

export default function OpsCasesScreen({
  tenantContext = defaultTenantContext,
  evidence,
  detailEvidence,
  fetchImpl,
  initialPathname,
  onEvidenceCapture,
  onNavigate,
}: OpsCasesScreenProps) {
  const pathname = initialPathname ?? window.location.pathname;
  const [opsState, setOpsState] = useState<OpsCasesState>(() => evidence ? { kind: 'loaded', view: evidence } : { kind: 'loading' });
  const [activeTab, setActiveTab] = useState<TabId>(() => tabFromPath(pathname));
  const [selectedCaseId, setSelectedCaseId] = useState<string | null>(() => caseIdFromOpsPath(pathname));
  const [detailState, setDetailState] = useState<OpsCaseDetailState>(() => detailEvidence ? { kind: 'loaded', detail: detailEvidence } : { kind: 'idle' });
  const [filters, setFilters] = useState<CaseFilters>(defaultFilters);
  const [owner, setOwner] = useState('');
  const [note, setNote] = useState('');
  const [statusReason, setStatusReason] = useState('');
  const [resolutionCode, setResolutionCode] = useState('');
  const [actionResult, setActionResult] = useState<OpsCaseActionResult | null>(null);

  useEffect(() => {
    if (evidence) {
      setOpsState({ kind: 'loaded', view: evidence });
      return;
    }

    let active = true;
    fetchOpsCases(fetchImpl)
      .then((view) => {
        if (!active) return;
        setOpsState({ kind: 'loaded', view });
        setSelectedCaseId((current) => current ?? view.cases[0]?.caseId ?? null);
      })
      .catch((error: unknown) => {
        const message = error instanceof Error ? error.message : 'Operations cases are unavailable.';
        if (active) setOpsState({ kind: 'unreachable', message });
      });
    return () => {
      active = false;
    };
  }, [evidence, fetchImpl]);

  useEffect(() => {
    if (detailEvidence) {
      setDetailState({ kind: 'loaded', detail: detailEvidence });
      return;
    }
    if (!selectedCaseId) {
      setDetailState({ kind: 'idle' });
      return;
    }

    let active = true;
    setDetailState({ kind: 'loading' });
    setActionResult(null);
    fetchOpsCaseDetail(selectedCaseId, fetchImpl)
      .then((detail) => {
        if (active) setDetailState({ kind: 'loaded', detail });
      })
      .catch((error: unknown) => {
        const message = error instanceof Error ? error.message : 'Operations case detail is unavailable.';
        if (active) setDetailState({ kind: 'unreachable', message });
      });
    return () => {
      active = false;
    };
  }, [detailEvidence, fetchImpl, selectedCaseId]);

  useEffect(() => {
    if (opsState.kind !== 'loaded') return;
    onEvidenceCapture?.({ screenId: 'ops-cases', state: stateForOpsCases(opsState.view), evidenceTarget, refs: collectOpsCaseEvidenceRefs(opsState.view) });
  }, [opsState, onEvidenceCapture]);

  const view = opsState.kind === 'loaded' ? normalizeOpsCasesView(opsState.view) : null;
  const filteredCases = useMemo(() => view ? filterCases(view.cases, filters) : [], [view, filters]);
  const selectedCase = selectedCaseId && view ? view.cases.find((opsCase) => opsCase.caseId === selectedCaseId) : null;

  function changeTab(tab: TabId) {
    setActiveTab(tab);
    onNavigate?.(tabRoutes[tab]);
  }

  function selectCase(caseId: string) {
    setSelectedCaseId(caseId);
    setActiveTab('cases');
    onNavigate?.(`/ops/cases/${encodeURIComponent(caseId)}`);
  }

  async function assignSelectedCase() {
    if (!selectedCaseId || !owner.trim()) return;
    setActionResult(await assignOpsCase(selectedCaseId, owner.trim(), fetchImpl));
  }

  async function addNoteToSelectedCase() {
    if (!selectedCaseId || !note.trim()) return;
    setActionResult(await addOpsCaseNote(selectedCaseId, note.trim(), fetchImpl));
  }

  async function updateSelectedCaseStatus(status: string) {
    if (!selectedCaseId || (!statusReason.trim() && !resolutionCode.trim())) return;
    setActionResult(await updateOpsCaseStatus(selectedCaseId, status, statusReason.trim(), resolutionCode.trim(), fetchImpl));
  }

  if (opsState.kind === 'loading') {
    return <section className="panel" aria-labelledby="ops-cases-heading"><h2 id="ops-cases-heading">Operations Cases</h2><p role="status">Loading operations cases...</p></section>;
  }

  if (opsState.kind === 'unreachable') {
    return (
      <section className="panel" aria-labelledby="ops-cases-heading">
        <h2 id="ops-cases-heading">Operations Cases</h2>
        <div className="banner banner--blocked" role="alert"><strong>Operations cases unavailable</strong><span>{opsState.message}</span></div>
      </section>
    );
  }

  if (!view) return null;

  return (
    <>
      <section className="hero hero--admin" aria-labelledby="ops-cases-title">
        <p className="eyebrow">Operations cases - PII-24-S19</p>
        <h2 id="ops-cases-title">Operations Cases</h2>
        <p>
          Review operations queues, cases, assignment context, timeline evidence, and escalations from backend-owned refs. The browser
          does not calculate SLA values, route cases, or apply mortgage pricing policy.
        </p>
      </section>

      <section className="panel ops-cases-sticky" aria-labelledby="ops-cases-heading">
        <div className="panel-heading-row">
          <div>
            <p className="eyebrow">Tenant context</p>
            <h2 id="ops-cases-heading">Operations case workspace</h2>
          </div>
          <button type="button" disabled aria-describedby="new-case-disabled">New Case</button>
        </div>
        <p id="new-case-disabled" className="field-help">New case creation stays disabled until operations-service supplies a write contract and audit reference.</p>
        <dl className="status-grid">
          <dt>Tenant</dt><dd>{displayText(view.tenantContext || tenantContext)}</dd>
          <dt>Status</dt><dd>{displayText(view.dependencyStatus)}</dd>
          <dt>Support reference</dt><dd><code>{view.uiTraceId}</code></dd>
          <dt>Evidence target</dt><dd><code>{evidenceTarget}</code></dd>
        </dl>
        <div className={stateForOpsCases(view) === 'blocked' ? 'banner banner--blocked' : 'banner banner--info'} role={stateForOpsCases(view) === 'blocked' ? 'alert' : 'status'}>
          <strong>{displayText(view.dependencyStatus)}</strong>
          <span>{displayText(view.fallbackReason)}</span>
          <span>Action policy: {view.actionsDisabled ? 'backend actions disabled or fixture-backed' : 'backend action contract supplied'}</span>
        </div>
        <DiagnosticsDetails items={[`Support reference: ${view.uiTraceId}`, `Workspace ${view.tenantContext}`, `Evidence target: ${evidenceTarget}`]} />
        <nav className="custom-rules-shell__nav" aria-label="Operations cases tabs">
          {(['dashboard', 'queues', 'cases', 'escalations'] as TabId[]).map((tab) => <button key={tab} type="button" aria-pressed={activeTab === tab} onClick={() => changeTab(tab)}>{displayText(tab)}</button>)}
        </nav>
      </section>

      {activeTab === 'dashboard' ? <DashboardPanel metrics={view.dashboardMetrics} charts={view.charts} onTab={changeTab} /> : null}
      {activeTab === 'queues' ? <QueuesPanel queues={view.queues} onQueue={(queue) => { setFilters((current) => ({ ...current, queue })); setActiveTab('cases'); }} /> : null}
      {activeTab === 'cases' ? <CasesPanel view={view} filters={filters} onFiltersChange={setFilters} cases={filteredCases} selectedCaseId={selectedCaseId} onSelectCase={selectCase} selectedCase={selectedCase} /> : null}
      {activeTab === 'escalations' ? <EscalationsPanel escalations={view.escalations} /> : null}

      <CaseDetailPanel
        detailState={detailState}
        owner={owner}
        note={note}
        statusReason={statusReason}
        resolutionCode={resolutionCode}
        actionsDisabled={view.actionsDisabled}
        actionResult={actionResult}
        onOwnerChange={setOwner}
        onNoteChange={setNote}
        onStatusReasonChange={setStatusReason}
        onResolutionCodeChange={setResolutionCode}
        onAssign={() => void assignSelectedCase()}
        onAddNote={() => void addNoteToSelectedCase()}
        onEscalate={() => void updateSelectedCaseStatus('ESCALATED')}
        onResolve={() => void updateSelectedCaseStatus('RESOLVED')}
      />

      <section className="panel" aria-labelledby="ops-events-heading">
        <h2 id="ops-events-heading">Events and blocked gaps</h2>
        <ExpandableRefList label="Operations case events" values={view.events} emptyText="No events supplied." open />
        <ExpandableRefList label="Known unavailable integration gaps" values={['live operations-service write actions', 'exception-service action contracts', 'Storybook visual states', 'E2E case lifecycle', 'export/download contracts', 'case routing logic', 'SLA calculation ownership']} emptyText="No blocked gaps recorded." open />
      </section>
    </>
  );
}

export function stateForOpsCases(view: OpsCaseListView) {
  const normalized = normalizeOpsCasesView(view);
  if (normalized.dependencyStatus.toLowerCase().includes('blocked')) return 'blocked';
  if (!normalized.cases.length && !normalized.queues.length && !normalized.escalations.length) return 'empty';
  return 'ready';
}

function DashboardPanel({ metrics, charts, onTab }: { metrics: OpsCaseMetric[]; charts: OpsCaseChart[]; onTab: (tab: TabId) => void }) {
  return (
    <section className="panel" aria-labelledby="ops-dashboard-heading">
      <div className="panel-heading-row"><div><p className="eyebrow">Dashboard</p><h2 id="ops-dashboard-heading">Dashboard metrics and charts</h2></div><button type="button" onClick={() => onTab('cases')}>View All Cases</button></div>
      {metrics.length === 0 ? <p role="status">No dashboard metrics supplied by operations-service.</p> : <div className="metrics-grid" role="list" aria-label="Operations case dashboard metrics">{metrics.map((metric) => <MetricCard key={metric.metricId} metric={metric} />)}</div>}
      <div className="module-rail__grid" role="list" aria-label="Operations case dashboard charts">
        {charts.map((chart) => <ChartCard key={chart.chartId} chart={chart} />)}
      </div>
      <button type="button" className="button-secondary" onClick={() => onTab('queues')}>View Queues</button>
    </section>
  );
}

function MetricCard({ metric }: { metric: OpsCaseMetric }) {
  return <article className="module-card module-card--light" role="listitem"><p className="module-card__route">{displayText(metric.label)}</p><strong className="module-card__title">{metric.value}</strong><dl><dt>Source</dt><dd><code>{metric.sourceRef}</code></dd>{metric.status ? <><dt>Status</dt><dd>{displayText(metric.status)}</dd></> : null}</dl></article>;
}

function ChartCard({ chart }: { chart: OpsCaseChart }) {
  return <article className="module-card" role="listitem"><p className="module-card__route">Chart</p><strong className="module-card__title">{chart.label}</strong><ul className="chip-list" aria-label={`${chart.label} segments`}>{chart.segments.map((segment) => <li key={`${chart.chartId}-${segment.label}`}><span>{segment.label}</span> <code>{segment.value}</code> <code>{segment.sourceRef}</code></li>)}</ul></article>;
}

function QueuesPanel({ queues, onQueue }: { queues: OpsCaseQueueSummary[]; onQueue: (queue: string) => void }) {
  return (
    <section className="panel" aria-labelledby="ops-queues-heading">
      <h2 id="ops-queues-heading">Queues by priority and SLA state</h2>
      {queues.length === 0 ? <p role="status">No queue summaries supplied by operations-service.</p> : <div className="module-rail__grid" role="list" aria-label="Operations case queues">{queues.map((queue) => <article key={queue.queueId} className="module-card module-card--light" role="listitem"><p className="module-card__route">{queue.queueId}</p><strong className="module-card__title">{queue.label}</strong><dl><dt>Cases</dt><dd><code>{queue.caseCount}</code></dd><dt>Average age</dt><dd><code>{queue.avgAgeRef}</code></dd><dt>SLA breach count</dt><dd><code>{queue.slaBreachCountRef}</code></dd><dt>Oldest case</dt><dd><code>{queue.oldestCaseRef}</code></dd><dt>Owner</dt><dd><code>{queue.ownerRef}</code></dd></dl><ExpandableRefList label={`${queue.label} priority refs`} values={queue.priorityRefs} emptyText="No priority refs supplied." /><button type="button" onClick={() => onQueue(queue.queueId)}>View Queue</button></article>)}</div>}
    </section>
  );
}

function CasesPanel({ view, filters, onFiltersChange, cases, selectedCaseId, onSelectCase, selectedCase }: { view: OpsCaseListView; filters: CaseFilters; onFiltersChange: (filters: CaseFilters) => void; cases: OpsCaseSummary[]; selectedCaseId: string | null; onSelectCase: (caseId: string) => void; selectedCase: OpsCaseSummary | null | undefined }) {
  const filterOptions = normalizeOpsCasesView(view).filters;
  return (
    <section className="panel" aria-labelledby="ops-cases-list-heading">
      <div className="panel-heading-row"><div><p className="eyebrow">Cases</p><h2 id="ops-cases-list-heading">Cases list, filters, and bulk action shell</h2></div><button type="button" disabled aria-describedby="bulk-actions-disabled">Bulk actions</button></div>
      <p id="bulk-actions-disabled" className="field-help">Bulk assign, status update, and escalation require backend-owned write/action contracts.</p>
      <div className="intake-form" role="group" aria-label="Operations case filters">
        <FilterSelect label="Queue" value={filters.queue} options={filterOptions.queues} onChange={(queue) => onFiltersChange({ ...filters, queue })} />
        <FilterSelect label="Priority" value={filters.priority} options={filterOptions.priorities} onChange={(priority) => onFiltersChange({ ...filters, priority })} />
        <FilterSelect label="SLA state" value={filters.slaState} options={filterOptions.slaStates} onChange={(slaState) => onFiltersChange({ ...filters, slaState })} />
        <FilterSelect label="Owner" value={filters.owner} options={filterOptions.owners} onChange={(owner) => onFiltersChange({ ...filters, owner })} />
        <FilterSelect label="Status" value={filters.status} options={filterOptions.statuses} onChange={(status) => onFiltersChange({ ...filters, status })} />
        <label>Sort<select value={filters.sort} onChange={(event) => onFiltersChange({ ...filters, sort: event.target.value })}><option value="age">Age</option><option value="priority">Priority</option><option value="sla">SLA</option></select></label>
      </div>
      <div className="quote-table" role="table" aria-label="Operations cases list">
        <div role="row" className="quote-table__row quote-table__row--head"><span role="columnheader">Case ID</span><span role="columnheader">Priority</span><span role="columnheader">Age</span><span role="columnheader">SLA State</span><span role="columnheader">Owner</span><span role="columnheader">Status</span><span role="columnheader">Context Summary</span><span role="columnheader">Action</span></div>
        {cases.length === 0 ? <div role="row" className="quote-table__row"><span role="cell">No operations cases match the current filters.</span></div> : cases.map((opsCase) => <CaseRow key={opsCase.caseId} opsCase={opsCase} selected={opsCase.caseId === selectedCaseId} onSelect={() => onSelectCase(opsCase.caseId)} />)}
      </div>
      {selectedCase ? <div className="banner banner--info" role="status">Selected case: <code>{selectedCase.caseId}</code> from {displayText(selectedCase.queue ?? 'queue not supplied')}.</div> : null}
    </section>
  );
}

function CaseRow({ opsCase, selected, onSelect }: { opsCase: OpsCaseSummary; selected: boolean; onSelect: () => void }) {
  return <div role="row" className={selected ? 'quote-table__row quote-table__row--selected' : 'quote-table__row'}><span role="cell"><code>{opsCase.caseId}</code></span><span role="cell">{opsCase.priority}</span><span role="cell"><code>{opsCase.ageLabel}</code></span><span role="cell">{displayText(opsCase.slaState)}</span><span role="cell">{opsCase.owner}</span><span role="cell">{displayText(opsCase.status)}</span><span role="cell">{opsCase.contextSummary}<ExpandableRefList label={`${opsCase.caseId} source refs`} values={opsCase.sourceRefs ?? []} emptyText="No source refs supplied." /></span><span role="cell"><button type="button" onClick={onSelect}>Open case</button></span></div>;
}

function EscalationsPanel({ escalations }: { escalations: OpsEscalationSummary[] }) {
  return (
    <section className="panel" aria-labelledby="ops-escalations-heading">
      <div className="panel-heading-row"><div><p className="eyebrow">Escalations</p><h2 id="ops-escalations-heading">Escalated cases and SLA breach refs</h2></div><button type="button" disabled>Acknowledge Selected</button></div>
      <div className="quote-table" role="table" aria-label="Operations case escalations">
        <div role="row" className="quote-table__row quote-table__row--head"><span role="columnheader">Case ID</span><span role="columnheader">Escalation Level</span><span role="columnheader">Escalated To</span><span role="columnheader">Escalated At</span><span role="columnheader">SLA Breach Time</span><span role="columnheader">Reason</span><span role="columnheader">Status</span><span role="columnheader">Actions</span></div>
        {escalations.length === 0 ? <div role="row" className="quote-table__row"><span role="cell">No escalations supplied by exception-service.</span></div> : escalations.map((escalation) => <div key={escalation.escalationId} role="row" className="quote-table__row"><span role="cell"><code>{escalation.caseId}</code></span><span role="cell">{displayText(escalation.escalationLevel)}</span><span role="cell"><code>{escalation.escalatedTo}</code></span><span role="cell"><code>{escalation.escalatedAtRef}</code></span><span role="cell"><code>{escalation.slaBreachTimeRef}</code></span><span role="cell">{escalation.reason}</span><span role="cell">{displayText(escalation.status)}</span><span role="cell"><button type="button" disabled>Acknowledge</button><button type="button" disabled>Resolve</button><button type="button" disabled>Re-escalate</button><ExpandableRefList label={`${escalation.escalationId} audit refs`} values={escalation.auditRefs} emptyText="No audit refs supplied." /></span></div>)}
      </div>
    </section>
  );
}

function CaseDetailPanel({ detailState, owner, note, statusReason, resolutionCode, actionsDisabled, actionResult, onOwnerChange, onNoteChange, onStatusReasonChange, onResolutionCodeChange, onAssign, onAddNote, onEscalate, onResolve }: { detailState: OpsCaseDetailState; owner: string; note: string; statusReason: string; resolutionCode: string; actionsDisabled: boolean; actionResult: OpsCaseActionResult | null; onOwnerChange: (value: string) => void; onNoteChange: (value: string) => void; onStatusReasonChange: (value: string) => void; onResolutionCodeChange: (value: string) => void; onAssign: () => void; onAddNote: () => void; onEscalate: () => void; onResolve: () => void }) {
  return (
    <section className="panel" aria-labelledby="ops-case-detail-heading">
      <h2 id="ops-case-detail-heading">Case detail timeline, evidence, and actions</h2>
      {detailState.kind === 'idle' ? <p>Select a case to inspect timeline and intervention controls.</p> : null}
      {detailState.kind === 'loading' ? <p role="status">Loading operations case detail...</p> : null}
      {detailState.kind === 'unreachable' ? <div className="banner banner--blocked" role="alert">{detailState.message}</div> : null}
      {detailState.kind === 'loaded' ? <CaseDetail detail={detailState.detail} owner={owner} note={note} statusReason={statusReason} resolutionCode={resolutionCode} actionsDisabled={actionsDisabled} onOwnerChange={onOwnerChange} onNoteChange={onNoteChange} onStatusReasonChange={onStatusReasonChange} onResolutionCodeChange={onResolutionCodeChange} onAssign={onAssign} onAddNote={onAddNote} onEscalate={onEscalate} onResolve={onResolve} /> : null}
      {actionResult ? <ActionResultBanner result={actionResult} /> : null}
    </section>
  );
}

function CaseDetail({ detail, owner, note, statusReason, resolutionCode, actionsDisabled, onOwnerChange, onNoteChange, onStatusReasonChange, onResolutionCodeChange, onAssign, onAddNote, onEscalate, onResolve }: { detail: OpsCaseDetail; owner: string; note: string; statusReason: string; resolutionCode: string; actionsDisabled: boolean; onOwnerChange: (value: string) => void; onNoteChange: (value: string) => void; onStatusReasonChange: (value: string) => void; onResolutionCodeChange: (value: string) => void; onAssign: () => void; onAddNote: () => void; onEscalate: () => void; onResolve: () => void }) {
  return (
    <div className="partner-detail-grid">
      <dl className="status-grid"><dt>Case</dt><dd><code>{detail.caseId}</code></dd><dt>Priority</dt><dd>{detail.priority}</dd><dt>SLA state</dt><dd>{displayText(detail.slaState)}</dd><dt>Owner</dt><dd>{detail.owner}</dd><dt>Status</dt><dd>{displayText(detail.status)}</dd><dt>Age</dt><dd><code>{detail.ageLabel}</code></dd></dl>
      <div className="quote-table" role="table" aria-label="Case timeline"><div role="row" className="quote-table__row quote-table__row--head"><span role="columnheader">Timestamp</span><span role="columnheader">Actor</span><span role="columnheader">Event</span><span role="columnheader">Summary</span><span role="columnheader">Evidence</span></div>{detail.timeline.map((event) => <div key={event.eventId} role="row" className="quote-table__row"><span role="cell"><code>{event.timestampRef ?? 'timestamp-ref:backend-owned'}</code></span><span role="cell"><code>{event.actorRef ?? 'actor-ref:backend-owned'}</code></span><span role="cell">{event.eventType}</span><span role="cell">{event.summary}</span><span role="cell"><ExpandableRefList label={`${event.eventId} evidence`} values={event.evidenceRefs ?? []} emptyText="No event evidence refs supplied." /></span></div>)}</div>
      <div className="quote-table" role="table" aria-label="Evidence packets"><div role="row" className="quote-table__row quote-table__row--head"><span role="columnheader">Packet</span><span role="columnheader">Type</span><span role="columnheader">Source</span><span role="columnheader">Download</span></div>{(detail.evidencePackets ?? detail.evidencePacketIds.map((packetId) => ({ packetId, packetType: 'BACKEND_PACKET', sourceRef: packetId, downloadRef: 'download-ref:backend-contract-required' }))).map((packet) => <div key={packet.packetId} role="row" className="quote-table__row"><span role="cell"><code>{packet.packetId}</code></span><span role="cell">{displayText(packet.packetType)}</span><span role="cell"><code>{packet.sourceRef}</code></span><span role="cell"><button type="button" disabled>{packet.downloadRef}</button></span></div>)}</div>
      <ActionForm label="Assign owner" inputId="ops-owner" value={owner} onChange={onOwnerChange} buttonLabel="Assign case" disabled={actionsDisabled || !owner.trim()} disabledReason="Assignment requires operations-service action contract." onSubmit={onAssign} refs={detail.assignmentRefs ?? []} />
      <ActionForm label="Add case note" inputId="ops-note" value={note} onChange={onNoteChange} buttonLabel="Add note" disabled={actionsDisabled || !note.trim()} disabledReason="Notes require operations-service action contract." onSubmit={onAddNote} refs={detail.noteRefs ?? []} multiline />
      <ActionForm label="Status or escalation reason" inputId="ops-status-reason" value={statusReason} onChange={onStatusReasonChange} buttonLabel="Escalate" disabled={actionsDisabled || !statusReason.trim()} disabledReason="Escalation requires exception-service action contract." onSubmit={onEscalate} refs={detail.escalationRefs ?? []} multiline />
      <ActionForm label="Resolution code" inputId="ops-resolution-code" value={resolutionCode} onChange={onResolutionCodeChange} buttonLabel="Resolve case" disabled={actionsDisabled || !resolutionCode.trim()} disabledReason="Status update requires operations-service action contract." onSubmit={onResolve} refs={detail.statusRefs ?? []} />
    </div>
  );
}

function ActionForm({ label, inputId, value, onChange, buttonLabel, disabled, disabledReason, onSubmit, refs, multiline = false }: { label: string; inputId: string; value: string; onChange: (value: string) => void; buttonLabel: string; disabled: boolean; disabledReason: string; onSubmit: () => void; refs: string[]; multiline?: boolean }) {
  return <div className="field-group"><label htmlFor={inputId}>{label}</label>{multiline ? <textarea id={inputId} value={value} onChange={(event) => onChange(event.target.value)} /> : <input id={inputId} value={value} onChange={(event) => onChange(event.target.value)} />}<button type="button" disabled={disabled} title={disabledReason} onClick={onSubmit}>{buttonLabel}</button><p className="field-help">{disabledReason}</p><ExpandableRefList label={`${label} refs`} values={refs} emptyText="No action refs supplied." /></div>;
}

function FilterSelect({ label, value, options, onChange }: { label: string; value: string; options: string[]; onChange: (value: string) => void }) {
  return <label>{label}<select value={value} onChange={(event) => onChange(event.target.value)}><option value="all">All {label.toLowerCase()}</option>{options.map((option) => <option key={option} value={option}>{displayText(option)}</option>)}</select></label>;
}

function ActionResultBanner({ result }: { result: OpsCaseActionResult }) {
  const accepted = result.status !== 'BLOCKED';
  return <div className={accepted ? 'banner banner--success' : 'banner banner--blocked'} role={accepted ? 'status' : 'alert'}><strong>{accepted ? 'Case action recorded' : 'Case action blocked'}</strong><span>{result.message ?? result.immutableSummary ?? displayText(result.status)}</span>{'downstreamExecuted' in result ? <span>Connected workflow run: {result.downstreamExecuted ? 'yes' : 'no'}</span> : null}<ExpandableRefList label="Action events" values={result.events} emptyText="No action events supplied." /></div>;
}

function ExpandableRefList({ label, values, emptyText, open = false }: { label: string; values: string[]; emptyText: string; open?: boolean }) {
  return <details className="blocker-details" open={open}><summary>{label}</summary>{values.length === 0 ? <p>{emptyText}</p> : <ul>{values.map((value) => <li key={value}><code>{value}</code></li>)}</ul>}</details>;
}

function normalizeOpsCasesView(view: OpsCaseListView): Required<Pick<OpsCaseListView, 'tenantContext' | 'dependencyStatus' | 'dashboardMetrics' | 'charts' | 'queues' | 'cases' | 'escalations' | 'filters' | 'actionsDisabled' | 'fallbackReason' | 'uiTraceId' | 'events'>> {
  const queues = view.queues ?? [];
  const cases = view.cases ?? [];
  return {
    tenantContext: view.tenantContext,
    dependencyStatus: view.dependencyStatus ?? 'OPERATIONS_CASES_READY_WITH_LOCAL_FALLBACK',
    dashboardMetrics: view.dashboardMetrics ?? [],
    charts: view.charts ?? [],
    queues,
    cases,
    escalations: view.escalations ?? [],
    filters: view.filters ?? {
      queues: uniqueValues([...queues.map((queue) => queue.queueId), ...cases.map((opsCase) => opsCase.queue ?? '').filter(Boolean)]),
      priorities: uniqueValues(cases.map((opsCase) => opsCase.priority)),
      slaStates: uniqueValues(cases.map((opsCase) => opsCase.slaState)),
      owners: uniqueValues(cases.map((opsCase) => opsCase.owner)),
      statuses: uniqueValues(cases.map((opsCase) => opsCase.status)),
    },
    actionsDisabled: view.actionsDisabled ?? true,
    fallbackReason: view.fallbackReason ?? 'Configured operations-service and exception-service contracts are unavailable; local UI shows backend refs only.',
    uiTraceId: view.uiTraceId,
    events: view.events,
  };
}

function filterCases(cases: OpsCaseSummary[], filters: CaseFilters) {
  return [...cases]
    .filter((opsCase) => filters.queue === 'all' || opsCase.queue === filters.queue)
    .filter((opsCase) => filters.priority === 'all' || opsCase.priority === filters.priority)
    .filter((opsCase) => filters.slaState === 'all' || opsCase.slaState === filters.slaState)
    .filter((opsCase) => filters.owner === 'all' || opsCase.owner === filters.owner)
    .filter((opsCase) => filters.status === 'all' || opsCase.status === filters.status)
    .sort((left, right) => sortCase(left, right, filters.sort));
}

function sortCase(left: OpsCaseSummary, right: OpsCaseSummary, sort: string) {
  if (sort === 'priority') return left.priority.localeCompare(right.priority);
  if (sort === 'sla') return left.slaState.localeCompare(right.slaState);
  return left.ageLabel.localeCompare(right.ageLabel);
}

function collectOpsCaseEvidenceRefs(view: OpsCaseListView) {
  const normalized = normalizeOpsCasesView(view);
  return uniqueValues([normalized.uiTraceId, evidenceTarget, ...normalized.dashboardMetrics.map((metric) => metric.sourceRef), ...normalized.charts.flatMap((chart) => chart.segments.map((segment) => segment.sourceRef)), ...normalized.queues.flatMap((queue) => [queue.avgAgeRef, queue.slaBreachCountRef, queue.oldestCaseRef, queue.ownerRef, ...queue.priorityRefs]), ...normalized.cases.flatMap((opsCase) => [opsCase.caseId, opsCase.ageLabel, ...(opsCase.sourceRefs ?? [])]), ...normalized.escalations.flatMap((escalation) => [escalation.escalationId, escalation.caseId, escalation.escalatedAtRef, escalation.slaBreachTimeRef, ...escalation.auditRefs]), ...normalized.events]);
}

function tabFromPath(pathname: string): TabId {
  if (pathname.startsWith('/ops/queues')) return 'queues';
  if (pathname.startsWith('/ops/cases')) return 'cases';
  if (pathname.startsWith('/ops/escalations')) return 'escalations';
  return 'dashboard';
}

function caseIdFromOpsPath(pathname: string) {
  const match = pathname.match(/^\/ops\/cases\/([^/]+)/);
  return match ? decodeURIComponent(match[1]) : null;
}

function uniqueValues(values: string[]) {
  return Array.from(new Set(values.filter(Boolean))).sort((a, b) => a.localeCompare(b));
}

function displayText(value: string | number | boolean | null | undefined) {
  if (value === null || value === undefined || value === '') return 'Not supplied';
  return String(value).replace(/[._/-]+/g, ' ').replace(/\b\w/g, (letter) => letter.toUpperCase());
}
