import { useEffect, useMemo, useState } from 'react';
import { DiagnosticsDetails } from '../../components/DiagnosticsDetails';
import {
  fetchRateFeedOperations,
  type RateFeedGridBlocker,
  type RateFeedHealthFeed,
  type RateFeedOperationsView,
  type RateFeedReplayEvidenceView,
  type RateFeedWorkflowStep,
} from '../../lib/api/rateFeedOps';

type RateFeedOpsState =
  | { kind: 'loading' }
  | { kind: 'loaded'; view: RateFeedOperationsView }
  | { kind: 'unreachable'; message: string };

type RateFeedOperationsScreenProps = {
  tenantContext?: string;
  evidence?: RateFeedOperationsView;
  fetchImpl?: typeof fetch;
  onEvidenceCapture?: (evidence: { screenId: string; state: string; evidenceTarget: string; refs: string[] }) => void;
  onNavigate?: (target: string) => void;
};

type BlockerFilters = {
  severity: string;
  code: string;
  resolution: string;
};

const evidenceTarget = '.local-harness/evidence/PII-24-S18/rate-feed-ops.json';
const defaultTenantContext = 'ui-preview-tenant';
const fallbackLastUpdatedRef = 'last-updated-ref:backend-owned-timestamp-required';

export default function RateFeedOperationsScreen({
  tenantContext = defaultTenantContext,
  evidence,
  fetchImpl,
  onEvidenceCapture,
  onNavigate,
}: RateFeedOperationsScreenProps) {
  const [opsState, setOpsState] = useState<RateFeedOpsState>(() => evidence ? { kind: 'loaded', view: evidence } : { kind: 'loading' });
  const [selectedStepId, setSelectedStepId] = useState<string | null>(null);
  const [selectedReplayRunId, setSelectedReplayRunId] = useState<string>('');
  const [filters, setFilters] = useState<BlockerFilters>({ severity: 'all', code: 'all', resolution: 'all' });
  const [selectedBlockers, setSelectedBlockers] = useState<string[]>([]);

  useEffect(() => {
    if (evidence) {
      setOpsState({ kind: 'loaded', view: evidence });
      return;
    }

    let active = true;
    fetchRateFeedOperations(tenantContext, fetchImpl)
      .then((view) => {
        if (active) setOpsState({ kind: 'loaded', view });
      })
      .catch((error: unknown) => {
        const message = error instanceof Error ? error.message : 'Rate feed operations are unavailable.';
        if (active) setOpsState({ kind: 'unreachable', message });
      });
    return () => {
      active = false;
    };
  }, [evidence, fetchImpl, tenantContext]);

  useEffect(() => {
    if (opsState.kind !== 'loaded') return;
    const view = opsState.view;
    const firstStep = view.workflowSteps[0]?.stepId ?? null;
    setSelectedStepId((current) => current ?? firstStep);
    const replay = replayViewFor(view.replayEvidence);
    setSelectedReplayRunId((current) => current || replay.selectedRunId || replay.runs[0]?.runId || '');
    onEvidenceCapture?.({
      screenId: 'rate-feed-ops',
      state: stateForRateFeedOperations(view),
      evidenceTarget,
      refs: collectRateFeedEvidenceRefs(view),
    });
  }, [opsState, onEvidenceCapture]);

  if (opsState.kind === 'loading') {
    return <section className="panel" aria-labelledby="rate-feed-heading"><h2 id="rate-feed-heading">Rate Feed Operations</h2><p role="status">Loading rate feed operations...</p></section>;
  }

  if (opsState.kind === 'unreachable') {
    return (
      <section className="panel" aria-labelledby="rate-feed-heading">
        <h2 id="rate-feed-heading">Rate Feed Operations</h2>
        <div className="banner banner--blocked" role="alert">
          <strong>Rate feed operations unavailable</strong>
          <span>{opsState.message}</span>
          <span>Connected rate-feed-service and integration-service refs are required; the UI will not synthesize feed adapter logic, pricing rules, or validation bounds.</span>
        </div>
      </section>
    );
  }

  const view = opsState.view;
  const replay = replayViewFor(view.replayEvidence);
  const filteredBlockers = filterBlockers(view.rowBlockers, filters);
  const selectedStep = view.workflowSteps.find((step) => step.stepId === selectedStepId) ?? view.workflowSteps[0];
  const selectedReplayRun = replay.runs.find((run) => run.runId === selectedReplayRunId) ?? replay.runs[0];
  const bulkDisabledReason = selectedBlockers.length === 0 ? 'Select row blockers before applying a backend-owned resolution action.' : 'Bulk resolve remains disabled until rate-feed-service supplies a write contract and audit reference.';

  return (
    <>
      <section className="hero hero--rate-feed" aria-labelledby="rate-feed-title">
        <p className="eyebrow">Rate-feed operations - PII-24-S18</p>
        <h2 id="rate-feed-title">Rate Feed Operations</h2>
        <p>
          Track rate feed workflow steps, row blockers, replay evidence, and feed health from backend-owned references. The browser
          displays refs and disabled action affordances only; it does not implement feed adapter logic, pricing rules, thresholds, or validation bounds.
        </p>
      </section>

      <section className="panel rate-feed-ops-sticky" aria-labelledby="rate-feed-heading">
        <div className="panel-heading-row">
          <div>
            <p className="eyebrow">Tenant context</p>
            <h2 id="rate-feed-heading">Rate feed operations workspace</h2>
          </div>
          <button type="button" disabled aria-describedby="rate-feed-refresh-disabled">Refresh</button>
        </div>
        <p id="rate-feed-refresh-disabled" className="field-help">Refresh polls are blocked in this local lane until the configured backend cache contract is available.</p>
        <dl className="status-grid">
          <dt>Tenant</dt><dd>{displayText(view.tenantContext || tenantContext)}</dd>
          <dt>Status</dt><dd>{displayText(view.dependencyStatus)}</dd>
          <dt>Last updated</dt><dd><code>{view.lastUpdatedRef ?? fallbackLastUpdatedRef}</code></dd>
          <dt>Support reference</dt><dd><code>{view.uiTraceId}</code></dd>
          <dt>Evidence target</dt><dd><code>{evidenceTarget}</code></dd>
        </dl>
        <div className={stateForRateFeedOperations(view) === 'blocked' ? 'banner banner--blocked' : 'banner banner--info'} role={stateForRateFeedOperations(view) === 'blocked' ? 'alert' : 'status'}>
          <strong>{displayText(view.dependencyStatus)}</strong>
          <span>{displayText(view.fallbackReason)}</span>
          <span>Action policy: {view.actionsDisabled ? 'backend actions disabled' : 'backend action contract supplied'}</span>
        </div>
        <DiagnosticsDetails items={[`Support reference: ${view.uiTraceId}`, `Last updated: ${view.lastUpdatedRef ?? fallbackLastUpdatedRef}`, `Evidence target: ${evidenceTarget}`]} />
      </section>

      <FeedHealthSummaryPanel view={view} onNavigate={onNavigate} />
      <WorkflowStepsPanel steps={view.workflowSteps} selectedStep={selectedStep} onSelectStep={setSelectedStepId} actionsDisabled={view.actionsDisabled} />
      <RowBlockersPanel blockers={filteredBlockers} allBlockers={view.rowBlockers} filters={filters} onFiltersChange={setFilters} selectedBlockers={selectedBlockers} onSelectedBlockersChange={setSelectedBlockers} bulkDisabledReason={bulkDisabledReason} />
      <ReplayEvidencePanel replay={replay} selectedRunId={selectedReplayRun?.runId ?? selectedReplayRunId} onSelectedRunIdChange={setSelectedReplayRunId} />

      <section className="panel" aria-labelledby="rate-feed-source-refs-heading">
        <h2 id="rate-feed-source-refs-heading">Audit, source references, and events</h2>
        <ExpandableRefList label="Backend source references" values={view.sourceReferences} emptyText="No source refs supplied." open />
        <ExpandableRefList label="Rate feed events" values={view.events} emptyText="No rate feed events supplied." />
      </section>
    </>
  );
}

export function stateForRateFeedOperations(view: RateFeedOperationsView) {
  if (view.dependencyStatus.toLowerCase().includes('blocked') || view.workflowSteps.length === 0) return 'blocked';
  if (view.rowBlockers.some((blocker) => blocker.resolutionState === 'OPEN') || !view.feedHealth) return 'needs-attention';
  return 'ready';
}

export function exportReplayEvidenceJson(view: RateFeedOperationsView) {
  return JSON.stringify({ evidenceTarget, exportedAt: 'local-preview', replayEvidence: replayViewFor(view.replayEvidence) }, null, 2);
}

function FeedHealthSummaryPanel({ view, onNavigate }: { view: RateFeedOperationsView; onNavigate?: (target: string) => void }) {
  const health = view.feedHealth;
  if (!health) {
    return (
      <section className="panel" aria-labelledby="feed-health-heading">
        <h2 id="feed-health-heading">Feed Health Summary</h2>
        <div className="banner banner--blocked" role="alert">Feed health refs are required from rate-feed-service; no local feed status inference is performed.</div>
      </section>
    );
  }

  return (
    <section className="panel" aria-labelledby="feed-health-heading">
      <div className="panel-heading-row"><div><p className="eyebrow">Feed health</p><h2 id="feed-health-heading">Feed Health Summary</h2></div></div>
      <div className="metrics-grid" role="list" aria-label="Rate feed health totals">
        <MetricCard label="Total Feeds" value={health.totalFeeds} />
        <MetricCard label="Healthy" value={health.healthy} />
        <MetricCard label="Degraded" value={health.degraded} />
        <MetricCard label="Failed" value={health.failed} />
        <MetricCard label="Last Successful Ingest" value={health.lastSuccessfulIngestRef} />
      </div>
      <div className="quote-table" role="table" aria-label="Investor feed health table">
        <div role="row" className="quote-table__row quote-table__row--head"><span role="columnheader">Feed</span><span role="columnheader">Investor</span><span role="columnheader">Last run</span><span role="columnheader">Status</span><span role="columnheader">Freshness</span><span role="columnheader">Rows</span><span role="columnheader">Blockers</span><span role="columnheader">Details</span></div>
        {health.feeds.map((feed) => <FeedHealthRow key={feed.feedId} feed={feed} onNavigate={onNavigate} />)}
      </div>
    </section>
  );
}

function MetricCard({ label, value }: { label: string; value: string | number }) {
  return <article className="module-card module-card--light" role="listitem"><p className="module-card__route">{label}</p><strong className="module-card__title">{displayText(String(value))}</strong></article>;
}

function FeedHealthRow({ feed, onNavigate }: { feed: RateFeedHealthFeed; onNavigate?: (target: string) => void }) {
  return (
    <div role="row" className="quote-table__row">
      <span role="cell"><code>{feed.feedId}</code><br />{displayText(feed.feedName)}</span>
      <span role="cell"><code>{feed.investorRef}</code></span>
      <span role="cell"><code>{feed.lastRunRef}</code></span>
      <span role="cell">{displayText(feed.status)}</span>
      <span role="cell"><code>{feed.freshnessRef}</code></span>
      <span role="cell"><code>{feed.rowCountRef}</code></span>
      <span role="cell"><code>{feed.blockerCountRef}</code></span>
      <span role="cell"><button type="button" onClick={() => onNavigate?.(`/partners/integrations?feed=${encodeURIComponent(feed.feedId)}`)}>View Feed Details</button><br /><code>{feed.detailTargetRef}</code></span>
    </div>
  );
}

function WorkflowStepsPanel({ steps, selectedStep, onSelectStep, actionsDisabled }: { steps: RateFeedWorkflowStep[]; selectedStep?: RateFeedWorkflowStep; onSelectStep: (stepId: string) => void; actionsDisabled: boolean }) {
  return (
    <section className="panel" aria-labelledby="workflow-steps-heading">
      <div className="panel-heading-row"><div><p className="eyebrow">Workflow steps</p><h2 id="workflow-steps-heading">Upload-to-replay workflow</h2></div></div>
      <div className="module-rail__grid" role="list" aria-label="Rate feed workflow steps">
        {steps.map((step) => (
          <article key={step.stepId} className="module-card module-card--light" role="listitem">
            <p className="module-card__route">{displayText(step.status)}</p>
            <button type="button" className="button-link" aria-pressed={selectedStep?.stepId === step.stepId} onClick={() => onSelectStep(step.stepId)}>{step.label}</button>
            <p className="field-help">Step ID: <code>{displayText(step.stepId)}</code></p>
            <dl><dt>Source boundary</dt><dd><code>{step.sourceBoundary}</code></dd><dt>Audit ref</dt><dd><code>{step.auditRef}</code></dd><dt>Result hash ref</dt><dd><code>{step.resultHashRef}</code></dd></dl>
            <button type="button" disabled={!step.actionPermitted} aria-describedby={`rerun-${step.stepId}-disabled`}>Re-run Step</button>
            <p id={`rerun-${step.stepId}-disabled`} className="field-help">Re-run requires a backend write/audit contract.</p>
          </article>
        ))}
      </div>
      {selectedStep ? <StepDetail step={selectedStep} /> : <p role="status">No workflow steps supplied.</p>}
      <div className="banner banner--info" role="status">
        <strong>Publish and processing actions disabled</strong>
        <span>Connected rate-feed-service write and audit contracts are required before browser actions can execute.</span>
        <button type="button" disabled={actionsDisabled}>Publish rate sheet</button>
        <button type="button" disabled={actionsDisabled}>Request processing review</button>
      </div>
      <p className="field-help">Configured polling interval: 30s via backend cache contract; local lane does not start live polling.</p>
    </section>
  );
}

function StepDetail({ step }: { step: RateFeedWorkflowStep }) {
  return (
    <aside className="panel panel--nested" aria-label={`${step.stepId} step detail`}>
      <h3>{displayText(step.stepId)} step detail</h3>
      <dl className="status-grid"><dt>Input</dt><dd><code>{step.inputRef ?? 'input-ref:backend-owned-ref-required'}</code></dd><dt>Output</dt><dd><code>{step.outputRef ?? 'output-ref:backend-owned-ref-required'}</code></dd><dt>Timing</dt><dd><code>{step.timingRef ?? 'timing-ref:backend-owned-ref-required'}</code></dd></dl>
      <ExpandableRefList label="Error refs" values={step.errorRefs ?? []} emptyText="No error refs supplied." />
      <ExpandableRefList label="Log refs" values={step.logRefs ?? []} emptyText="No log refs supplied." />
    </aside>
  );
}

function RowBlockersPanel({ blockers, allBlockers, filters, onFiltersChange, selectedBlockers, onSelectedBlockersChange, bulkDisabledReason }: { blockers: RateFeedGridBlocker[]; allBlockers: RateFeedGridBlocker[]; filters: BlockerFilters; onFiltersChange: (filters: BlockerFilters) => void; selectedBlockers: string[]; onSelectedBlockersChange: (values: string[]) => void; bulkDisabledReason: string }) {
  const severities = uniqueValues(allBlockers.map((blocker) => blocker.severity));
  const codes = uniqueValues(allBlockers.map((blocker) => blocker.blockerCode));
  const resolutions = uniqueValues(allBlockers.map((blocker) => blocker.resolutionState));
  return (
    <section className="panel" aria-labelledby="row-blockers-heading">
      <div className="panel-heading-row"><div><p className="eyebrow">Row blockers</p><h2 id="row-blockers-heading">Row blocker resolution tracking</h2></div></div>
      <div className="intake-form" role="group" aria-label="Row blocker filters">
        <label>Severity<select value={filters.severity} onChange={(event) => onFiltersChange({ ...filters, severity: event.target.value })}><option value="all">All severities</option>{severities.map((value) => <option key={value} value={value}>{displayText(value)}</option>)}</select></label>
        <label>Blocker code<select value={filters.code} onChange={(event) => onFiltersChange({ ...filters, code: event.target.value })}><option value="all">All codes</option>{codes.map((value) => <option key={value} value={value}>{value}</option>)}</select></label>
        <label>Resolution<select value={filters.resolution} onChange={(event) => onFiltersChange({ ...filters, resolution: event.target.value })}><option value="all">All resolutions</option>{resolutions.map((value) => <option key={value} value={value}>{displayText(value)}</option>)}</select></label>
      </div>
      <div className="quote-table" role="table" aria-label="Rate grid blockers">
        <div role="row" className="quote-table__row quote-table__row--head"><span role="columnheader">Select</span><span role="columnheader">Row Ref</span><span role="columnheader">Field Name</span><span role="columnheader">Severity</span><span role="columnheader">Blocker Code</span><span role="columnheader">Source Reference</span><span role="columnheader">Resolution State</span><span role="columnheader">Details</span></div>
        {blockers.length === 0 ? <div role="row" className="quote-table__row"><span role="cell">No row blockers match the current filters.</span></div> : blockers.map((blocker) => <RowBlockerRow key={`${blocker.rowRef}-${blocker.fieldName}-${blocker.blockerCode}`} blocker={blocker} selected={selectedBlockers.includes(blocker.rowRef)} onToggle={(selected) => onSelectedBlockersChange(selected ? uniqueValues([...selectedBlockers, blocker.rowRef]) : selectedBlockers.filter((rowRef) => rowRef !== blocker.rowRef))} />)}
      </div>
      <div className="banner banner--info" role="status">
        <strong>Bulk resolve disabled</strong><span>{bulkDisabledReason}</span>
        <button type="button" disabled>Apply same resolution</button>
      </div>
    </section>
  );
}

function RowBlockerRow({ blocker, selected, onToggle }: { blocker: RateFeedGridBlocker; selected: boolean; onToggle: (selected: boolean) => void }) {
  return (
    <div role="row" className="quote-table__row">
      <span role="cell"><input aria-label={`Select ${blocker.rowRef}`} type="checkbox" checked={selected} onChange={(event) => onToggle(event.target.checked)} /></span>
      <span role="cell"><code>{blocker.rowRef}</code></span>
      <span role="cell">{displayText(blocker.fieldName)}</span>
      <span role="cell">{displayText(blocker.severity)}</span>
      <span role="cell"><strong>{displayText(blocker.blockerCode)}</strong></span>
      <span role="cell"><code>{blocker.sourceReference}</code></span>
      <span role="cell">{displayText(blocker.resolutionState)}</span>
      <span role="cell"><details><summary>Backend refs and remediation</summary><dl><dt>Expected</dt><dd><code>{blocker.expectedRef ?? 'expected-ref:backend-owned-ref-required'}</code></dd><dt>Actual</dt><dd><code>{blocker.actualRef ?? 'actual-ref:backend-owned-ref-required'}</code></dd><dt>Remediation</dt><dd><code>{blocker.remediationRef ?? 'remediation-ref:backend-owned-ref-required'}</code></dd></dl><ExpandableRefList label="Row data refs" values={blocker.rowDataRefs ?? []} emptyText="No row data refs supplied." /><ActionButtons actions={blocker.resolutionActions ?? ['Manual override', 'Re-ingest', 'Exclude row']} disabledReason="Resolution actions require backend audit/write contracts." /></details></span>
    </div>
  );
}

function ReplayEvidencePanel({ replay, selectedRunId, onSelectedRunIdChange }: { replay: RateFeedReplayEvidenceView; selectedRunId: string; onSelectedRunIdChange: (value: string) => void }) {
  const selectedRun = replay.runs.find((run) => run.runId === selectedRunId) ?? replay.runs[0];
  const [exported, setExported] = useState('');
  const exportDisabled = !replay.exportAvailable;
  return (
    <section className="panel" aria-labelledby="replay-evidence-heading">
      <div className="panel-heading-row"><div><p className="eyebrow">Replay evidence</p><h2 id="replay-evidence-heading">Replay Evidence and Diff View</h2></div><button type="button" disabled={exportDisabled} onClick={() => setExported(JSON.stringify(replay, null, 2))}>Export Diff</button></div>
      <label>Feed run selector<select value={selectedRun?.runId ?? ''} onChange={(event) => onSelectedRunIdChange(event.target.value)}>{replay.runs.map((run) => <option key={run.runId} value={run.runId}>{run.feedName} - {run.runId}</option>)}</select></label>
      {exportDisabled ? <p className="field-help">Export disabled until live export integration supplies an export contract. {replay.exportRef ? <code>{replay.exportRef}</code> : null}</p> : null}
      {selectedRun ? <dl className="status-grid"><dt>Run date ref</dt><dd><code>{selectedRun.runDateRef}</code></dd><dt>Version refs</dt><dd>{selectedRun.versionRefs.map((ref) => <code key={ref}>{ref} </code>)}</dd></dl> : <p role="status">No replay runs supplied.</p>}
      <div className="quote-table" role="table" aria-label="Replay diff table">
        <div role="row" className="quote-table__row quote-table__row--head"><span role="columnheader">Change</span><span role="columnheader">Row</span><span role="columnheader">Field</span><span role="columnheader">Original</span><span role="columnheader">Replay</span><span role="columnheader">Source</span></div>
        {replay.diffs.length === 0 ? <div role="row" className="quote-table__row"><span role="cell">No replay diffs supplied.</span></div> : replay.diffs.map((diff) => <div key={diff.diffId} role="row" className="quote-table__row"><span role="cell">{displayText(diff.changeType)}</span><span role="cell"><code>{diff.rowRef}</code></span><span role="cell">{displayText(diff.fieldName)}</span><span role="cell"><code>{diff.originalRef}</code></span><span role="cell"><code>{diff.replayRef}</code></span><span role="cell"><code>{diff.sourceRef}</code></span></div>)}
      </div>
      <ExpandableRefList label="Missing dependency blockers" values={replay.missingDependencyBlockers} emptyText="No missing dependency blockers supplied." open />
      <ExpandableRefList label="Version refs" values={replay.versionRefs} emptyText="No version refs supplied." />
      {exported ? <textarea aria-label="Exported rate feed replay diff" readOnly value={exported} /> : null}
    </section>
  );
}

function ActionButtons({ actions, disabledReason }: { actions: string[]; disabledReason: string }) {
  return <div className="button-row" aria-label="Disabled backend actions">{actions.map((action) => <button key={action} type="button" disabled title={disabledReason}>{action}</button>)}</div>;
}

function ExpandableRefList({ label, values, emptyText, open = false }: { label: string; values: string[]; emptyText: string; open?: boolean }) {
  return <details open={open}><summary>{label}</summary>{values.length ? <ul>{values.map((value) => <li key={value}><code>{value}</code></li>)}</ul> : <p>{emptyText}</p>}</details>;
}

function replayViewFor(replayEvidence: RateFeedOperationsView['replayEvidence']): RateFeedReplayEvidenceView {
  if (!Array.isArray(replayEvidence)) return replayEvidence;
  return {
    selectedRunId: replayEvidence[0] ?? '',
    runs: replayEvidence.map((ref, index) => ({ runId: ref, feedName: `Backend replay ref ${index + 1}`, runDateRef: ref, versionRefs: [] })),
    diffs: [],
    missingDependencyBlockers: [],
    versionRefs: replayEvidence,
    exportAvailable: false,
    exportRef: 'export-ref:live-export-contract-required',
  };
}

function filterBlockers(blockers: RateFeedGridBlocker[], filters: BlockerFilters) {
  return blockers.filter((blocker) => (filters.severity === 'all' || blocker.severity === filters.severity) && (filters.code === 'all' || blocker.blockerCode === filters.code) && (filters.resolution === 'all' || blocker.resolutionState === filters.resolution));
}

function collectRateFeedEvidenceRefs(view: RateFeedOperationsView) {
  const replay = replayViewFor(view.replayEvidence);
  return uniqueValues([
    view.uiTraceId,
    view.lastUpdatedRef ?? fallbackLastUpdatedRef,
    ...view.sourceReferences,
    ...view.workflowSteps.flatMap((step) => [step.auditRef, step.resultHashRef, step.inputRef, step.outputRef, step.timingRef, ...(step.errorRefs ?? []), ...(step.logRefs ?? [])].filter(Boolean) as string[]),
    ...view.rowBlockers.flatMap((blocker) => [blocker.sourceReference, blocker.expectedRef, blocker.actualRef, blocker.remediationRef, ...(blocker.rowDataRefs ?? [])].filter(Boolean) as string[]),
    ...(view.feedHealth?.feeds.flatMap((feed) => [feed.feedId, feed.investorRef, feed.lastRunRef, feed.freshnessRef, feed.rowCountRef, feed.blockerCountRef, feed.detailTargetRef]) ?? []),
    ...replay.runs.flatMap((run) => [run.runId, run.runDateRef, ...run.versionRefs]),
    ...replay.diffs.flatMap((diff) => [diff.diffId, diff.rowRef, diff.originalRef, diff.replayRef, diff.sourceRef]),
    ...replay.missingDependencyBlockers,
    ...replay.versionRefs,
    ...view.events,
  ]);
}

function uniqueValues(values: string[]) {
  return Array.from(new Set(values.filter(Boolean)));
}

function displayText(value: string) {
  return value.replace(/[._/-]+/g, ' ').replace(/\b\w/g, (letter) => letter.toUpperCase());
}
