import { useEffect, useState } from 'react';
import {
  fetchAdjustmentEvidence,
  type AdjustmentBlockedState,
  type AdjustmentConflictView,
  type AdjustmentEvidenceRow,
  type AdjustmentEvidenceView,
  type AdjustmentSummaryCard,
} from '../../lib/api/adjustments';

type AdjustmentEvidenceState =
  | { kind: 'loading' }
  | { kind: 'loaded'; view: AdjustmentEvidenceView }
  | { kind: 'unreachable'; message: string };

type AdjustmentEvidenceScreenProps = {
  tenantContext?: string;
  evidence?: AdjustmentEvidenceView;
  fetchImpl?: typeof fetch;
  onEvidenceCapture?: (evidence: { screenId: string; state: string; evidenceTarget: string; refs: string[] }) => void;
  onNavigate?: (target: string) => void;
};

const evidenceTarget = '.local-harness/evidence/PII-24-S15/adjustment-evidence.json';
const defaultTenantContext = 'ui-preview-tenant';

export default function AdjustmentEvidenceScreen({
  tenantContext = defaultTenantContext,
  evidence,
  fetchImpl,
  onEvidenceCapture,
  onNavigate,
}: AdjustmentEvidenceScreenProps) {
  const [adjustmentState, setAdjustmentState] = useState<AdjustmentEvidenceState>(() => evidence ? { kind: 'loaded', view: evidence } : { kind: 'loading' });
  const [activeTab, setActiveTab] = useState<'adjustments' | 'conflicts' | 'compensation' | 'summaries'>('adjustments');
  const [categoryFilter, setCategoryFilter] = useState('all');
  const [statusFilter, setStatusFilter] = useState('all');
  const [conflictFilter, setConflictFilter] = useState(false);
  const [exported, setExported] = useState('');

  useEffect(() => {
    if (evidence) {
      setAdjustmentState({ kind: 'loaded', view: evidence });
      return;
    }

    let active = true;
    fetchAdjustmentEvidence(tenantContext, fetchImpl)
      .then((view) => {
        if (active) setAdjustmentState({ kind: 'loaded', view });
      })
      .catch((error: unknown) => {
        const message = error instanceof Error ? error.message : 'Adjustment evidence is unavailable.';
        if (active) setAdjustmentState({ kind: 'unreachable', message });
      });
    return () => {
      active = false;
    };
  }, [evidence, fetchImpl, tenantContext]);

  useEffect(() => {
    if (adjustmentState.kind !== 'loaded') return;
    onEvidenceCapture?.({
      screenId: 'adjustment-evidence',
      state: stateForAdjustmentEvidence(adjustmentState.view),
      evidenceTarget,
      refs: [adjustmentState.view.replayHash, ...adjustmentState.view.versionRefs, ...adjustmentState.view.auditRefs],
    });
  }, [adjustmentState, onEvidenceCapture]);

  if (adjustmentState.kind === 'loading') {
    return <section className="panel" aria-labelledby="adjustment-heading"><h2 id="adjustment-heading">Adjustment Evidence</h2><p role="status">Loading adjustment evidence...</p></section>;
  }

  if (adjustmentState.kind === 'unreachable') {
    return (
      <section className="panel" aria-labelledby="adjustment-heading">
        <h2 id="adjustment-heading">Adjustment Evidence</h2>
        <div className="banner banner--blocked" role="alert">
          <strong>Adjustment evidence unavailable</strong>
          <span>{adjustmentState.message}</span>
          <span>Connected adjustment-service evidence is required; the UI will not synthesize adjustment rows.</span>
        </div>
      </section>
    );
  }

  const view = adjustmentState.view;
  const categories = uniqueValues(view.adjustments.map((adjustment) => adjustment.category));
  const statuses = uniqueValues(view.adjustments.map((adjustment) => adjustment.status));
  const filteredAdjustments = view.adjustments.filter((adjustment) => {
    if (categoryFilter !== 'all' && adjustment.category !== categoryFilter) return false;
    if (statusFilter !== 'all' && adjustment.status !== statusFilter) return false;
    if (conflictFilter && adjustment.conflictIds.length === 0) return false;
    return true;
  });

  return (
    <>
      <section className="hero hero--admin" aria-labelledby="adjustment-title">
        <p className="eyebrow">Adjustment service - PII-24-S15</p>
        <h2 id="adjustment-title">Adjustment Evidence</h2>
        <p>
          Inspect backend-supplied adjustment IDs, fact references, source versions, conflict ownership, compensation hooks,
          summaries, and replay references without calculating LLPA, fee, price, or compensation values in the browser.
        </p>
      </section>

      <section className="panel" aria-labelledby="adjustment-heading">
        <div className="panel-heading-row">
          <div>
            <p className="eyebrow">Tenant context</p>
            <h2 id="adjustment-heading">Adjustment evidence workspace</h2>
          </div>
          <button type="button" onClick={() => setExported(exportAdjustmentEvidenceJson(view))}>Export evidence JSON</button>
        </div>
        <dl className="status-grid">
          <dt>Tenant</dt><dd>{displayText(view.tenantContext || tenantContext)}</dd>
          <dt>Status</dt><dd>{displayText(view.status)}</dd>
          <dt>Support reference</dt><dd><code>{view.uiTraceId}</code></dd>
          <dt>Replay hash</dt><dd><code>{view.replayHash}</code></dd>
          <dt>Evidence target</dt><dd><code>{evidenceTarget}</code></dd>
        </dl>
        <div className={stateForAdjustmentEvidence(view) === 'blocked' ? 'banner banner--blocked' : 'banner banner--info'} role={stateForAdjustmentEvidence(view) === 'blocked' ? 'alert' : 'status'}>
          <strong>{displayText(view.status)}</strong>
          <span>{displayText(view.dependencyStatus)}</span>
          <span>{displayText(view.fallbackReason)}</span>
        </div>
        <nav className="custom-rules-shell__nav" aria-label="Adjustment evidence tabs">
          {(['adjustments', 'conflicts', 'compensation', 'summaries'] as const).map((tab) => (
            <button key={tab} type="button" aria-pressed={activeTab === tab} onClick={() => setActiveTab(tab)}>{displayText(tab)}</button>
          ))}
        </nav>
        {exported ? <textarea aria-label="Exported adjustment evidence" readOnly value={exported} /> : null}
      </section>

      {activeTab === 'adjustments' ? (
        <section className="panel" aria-labelledby="adjustment-table-heading">
          <div className="panel-heading-row">
            <div>
              <p className="eyebrow">Adjustments</p>
              <h2 id="adjustment-table-heading">Adjustment IDs, fact refs, sources, and summaries</h2>
            </div>
          </div>
          <div className="intake-form" role="group" aria-label="Adjustment filters">
            <label>Category<select value={categoryFilter} onChange={(event) => setCategoryFilter(event.target.value)}><option value="all">All categories</option>{categories.map((category) => <option key={category} value={category}>{displayText(category)}</option>)}</select></label>
            <label>Status<select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)}><option value="all">All statuses</option>{statuses.map((status) => <option key={status} value={status}>{displayText(status)}</option>)}</select></label>
            <label><input type="checkbox" checked={conflictFilter} onChange={(event) => setConflictFilter(event.target.checked)} /> Has conflicts</label>
          </div>
          <div className="quote-table" role="table" aria-label="Adjustment evidence table">
            <div role="row" className="quote-table__row quote-table__row--head">
              <span role="columnheader">Adjustment ID</span>
              <span role="columnheader">Label</span>
              <span role="columnheader">Category</span>
              <span role="columnheader">Status</span>
              <span role="columnheader">Fact refs</span>
              <span role="columnheader">Source</span>
              <span role="columnheader">Summary and hooks</span>
            </div>
            {filteredAdjustments.length === 0 ? <div role="row" className="quote-table__row"><span role="cell">No adjustment rows match the current filters.</span></div> : filteredAdjustments.map((adjustment) => <AdjustmentRow key={adjustment.adjustmentId} adjustment={adjustment} onNavigate={onNavigate} />)}
          </div>
        </section>
      ) : null}

      {activeTab === 'conflicts' ? <ConflictsPanel conflicts={view.conflicts} blockers={view.blockers} onNavigate={onNavigate} /> : null}
      {activeTab === 'compensation' ? <CompensationHooks adjustments={view.adjustments} onNavigate={onNavigate} /> : null}
      {activeTab === 'summaries' ? <Summaries view={view} /> : null}
    </>
  );
}

export function stateForAdjustmentEvidence(view: AdjustmentEvidenceView) {
  if (view.status.toLowerCase().includes('blocked') || view.blockers.length > 0) return 'blocked';
  if (view.conflicts.length > 0) return 'needs-attention';
  return view.adjustments.length === 0 ? 'empty' : 'ready';
}

export function exportAdjustmentEvidenceJson(view: AdjustmentEvidenceView) {
  return JSON.stringify({ evidenceTarget, exportedAt: 'local-preview', view }, null, 2);
}

function AdjustmentRow({ adjustment, onNavigate }: { adjustment: AdjustmentEvidenceRow; onNavigate?: (target: string) => void }) {
  return (
    <div role="row" className="quote-table__row">
      <span role="cell"><code>{adjustment.adjustmentId}</code></span>
      <span role="cell"><strong>{displayText(adjustment.label)}</strong></span>
      <span role="cell">{displayText(adjustment.category)}</span>
      <span role="cell">{displayText(adjustment.status)}</span>
      <span role="cell"><ExpandableRefList label={`${adjustment.adjustmentId} fact refs`} values={adjustment.factRefs} emptyText="No fact refs supplied." /></span>
      <span role="cell"><code>{adjustment.sourceRef}</code><br />Version <code>{adjustment.sourceVersionRef}</code><br /><button type="button" onClick={() => onNavigate?.(`/quote/preview/pricing-waterfall?adjustment=${encodeURIComponent(adjustment.adjustmentId)}`)}>View in Waterfall</button></span>
      <span role="cell">{displayText(adjustment.summary)}<ExpandableRefList label={`${adjustment.adjustmentId} compensation hooks`} values={adjustment.compensationHooks} emptyText="No compensation hooks for this adjustment." /><ExpandableRefList label={`${adjustment.adjustmentId} conflict ids`} values={adjustment.conflictIds} emptyText="No conflicts detected for this adjustment." /></span>
    </div>
  );
}

function ConflictsPanel({ conflicts, blockers, onNavigate }: { conflicts: AdjustmentConflictView[]; blockers: AdjustmentBlockedState[]; onNavigate?: (target: string) => void }) {
  return (
    <section className="panel" aria-labelledby="adjustment-conflicts-heading">
      <h2 id="adjustment-conflicts-heading">Conflicts, blockers, and resolution ownership</h2>
      {conflicts.length === 0 ? <div className="banner banner--info" role="status">No conflicts detected</div> : (
        <div className="quote-table" role="table" aria-label="Adjustment conflicts">
          <div role="row" className="quote-table__row quote-table__row--head"><span role="columnheader">Conflict ID</span><span role="columnheader">Severity</span><span role="columnheader">Reason</span><span role="columnheader">Resolution owner</span><span role="columnheader">Affected adjustments</span></div>
          {conflicts.map((conflict) => (
            <div key={conflict.conflictId} role="row" className="quote-table__row">
              <span role="cell"><code>{conflict.conflictId}</code></span>
              <span role="cell">{displayText(conflict.severity)}</span>
              <span role="cell"><strong>{displayText(conflict.reasonCode)}</strong><br />{displayText(conflict.reason)}</span>
              <span role="cell">{displayText(conflict.resolutionOwner)}<br /><button type="button" aria-label={`Assign ${conflict.conflictId} to me`}>Assign to Me</button></span>
              <span role="cell"><ExpandableRefList label={`${conflict.conflictId} affected adjustments`} values={conflict.affectedAdjustmentIds} emptyText="No affected adjustment IDs supplied." /><button type="button" onClick={() => onNavigate?.(`/pricing/adjustments?conflict=${encodeURIComponent(conflict.conflictId)}`)}>View Affected Adjustments</button></span>
            </div>
          ))}
        </div>
      )}
      <div className="offer-list" role="list" aria-label="Adjustment blockers">
        {blockers.map((blocker) => <AdjustmentBlockerCard key={blocker.reasonCode} blocker={blocker} />)}
      </div>
    </section>
  );
}

function AdjustmentBlockerCard({ blocker }: { blocker: AdjustmentBlockedState }) {
  return (
    <article className="offer-card" role="listitem" aria-label={`${blocker.reasonCode} blocked state`}>
      <h3>{displayText(blocker.reasonCode)}</h3>
      <p>{displayText(blocker.message)}</p>
      <dl className="status-grid"><dt>Source</dt><dd><code>{blocker.sourceRef}</code></dd><dt>Resolution owner</dt><dd>{displayText(blocker.resolutionOwner)}</dd></dl>
    </article>
  );
}

function CompensationHooks({ adjustments, onNavigate }: { adjustments: AdjustmentEvidenceRow[]; onNavigate?: (target: string) => void }) {
  const rows = adjustments.flatMap((adjustment) => adjustment.compensationHooks.map((hook) => ({ hook, adjustment })));
  return (
    <section className="panel" aria-labelledby="compensation-hooks-heading">
      <h2 id="compensation-hooks-heading">Compensation Hooks</h2>
      <p className="field-help">Formula, input, output, and audit values are rendered only when backend refs are supplied. This screen does not calculate compensation.</p>
      {rows.length === 0 ? <div className="banner banner--info" role="status">No compensation hooks for this adjustment evidence view.</div> : (
        <div className="module-rail__grid" role="list" aria-label="Compensation hook refs">
          {rows.map(({ hook, adjustment }) => (
            <article key={`${adjustment.adjustmentId}-${hook}`} className="module-card" role="listitem">
              <p className="module-card__route"><code>{hook}</code></p>
              <strong className="module-card__title">{displayText(adjustment.label)}</strong>
              <dl><dt>Adjustment ref</dt><dd><code>{adjustment.adjustmentId}</code></dd><dt>Compensation type</dt><dd>{compensationTypeFromHook(hook)}</dd><dt>Formula ref</dt><dd><code>{hook}</code></dd><dt>Status</dt><dd>{displayText(adjustment.status)}</dd></dl>
              <button type="button" onClick={() => onNavigate?.(`/pricing/margins?hook=${encodeURIComponent(hook)}`)}>View Margin Profitability</button>
            </article>
          ))}
        </div>
      )}
    </section>
  );
}

function Summaries({ view }: { view: AdjustmentEvidenceView }) {
  return (
    <section className="panel" aria-labelledby="adjustment-summary-heading">
      <div className="panel-heading-row"><div><p className="eyebrow">Summaries</p><h2 id="adjustment-summary-heading">Category summaries, version refs, audit refs, and replay hash</h2></div></div>
      <p className="field-help">Totals and bps values are shown only if supplied in backend summaries; no browser-side adjustment math is performed.</p>
      <div className="offer-list" role="list" aria-label="Adjustment summaries">
        {view.summaries.map((summary) => <AdjustmentSummaryEvidenceCard key={summary.category} summary={summary} />)}
      </div>
      <ExpandableRefList label="Adjustment version refs" values={view.versionRefs} emptyText="No version refs supplied." open />
      <ExpandableRefList label="Adjustment audit refs" values={view.auditRefs} emptyText="No audit refs supplied." open />
      <ExpandableRefList label="Adjustment events" values={view.events} emptyText="No adjustment events supplied." />
      <dl className="status-grid"><dt>Replay hash</dt><dd><code>{view.replayHash}</code></dd></dl>
    </section>
  );
}

function AdjustmentSummaryEvidenceCard({ summary }: { summary: AdjustmentSummaryCard }) {
  return (
    <article className="offer-card" role="listitem" aria-label={`${summary.category} summary`}>
      <h3>{displayText(summary.category)}</h3>
      <p>{displayText(summary.summary)}</p>
      <ExpandableRefList label={`${summary.category} evidence refs`} values={summary.evidenceRefs} emptyText="No summary evidence refs supplied." open />
    </article>
  );
}

function ExpandableRefList({ label, values, emptyText, open = false }: { label: string; values: string[]; emptyText: string; open?: boolean }) {
  return (
    <details className="blocker-details" open={open}>
      <summary>{label}</summary>
      {values.length === 0 ? <p>{emptyText}</p> : <ul>{values.map((value) => <li key={value}><code>{value}</code></li>)}</ul>}
    </details>
  );
}

function displayText(value: string | number | null | undefined) {
  if (value === null || value === undefined || value === '') return 'Not supplied';
  return String(value).replace(/[_-]+/g, ' ');
}

function uniqueValues(values: string[]) {
  return Array.from(new Set(values.filter(Boolean))).sort((a, b) => a.localeCompare(b));
}

function compensationTypeFromHook(hook: string) {
  const normalized = hook.toLowerCase();
  if (normalized.includes('branch')) return 'Branch compensation ref';
  if (normalized.includes('company')) return 'Company compensation ref';
  if (normalized.includes('lo')) return 'LO compensation ref';
  return 'Backend compensation hook ref';
}
