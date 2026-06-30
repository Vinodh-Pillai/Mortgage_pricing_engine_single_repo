import { useEffect, useState } from 'react';
import {
  fetchMarginProfitability,
  type MarginApprovalView,
  type MarginEvidenceSection,
  type MarginFloorEvidence,
  type MarginProfitabilityView,
} from '../../lib/api/marginProfitability';

type MarginProfitabilityState =
  | { kind: 'loading' }
  | { kind: 'loaded'; view: MarginProfitabilityView }
  | { kind: 'unreachable'; message: string };

type MarginProfitabilityScreenProps = {
  tenantContext?: string;
  evidence?: MarginProfitabilityView;
  fetchImpl?: typeof fetch;
  onEvidenceCapture?: (evidence: { screenId: string; state: string; evidenceTarget: string; refs: string[] }) => void;
  onNavigate?: (target: string) => void;
};

const evidenceTarget = '.local-harness/evidence/PII-24-S16/margin-profitability.json';
const defaultTenantContext = 'ui-preview-tenant';

export default function MarginProfitabilityScreen({
  tenantContext = defaultTenantContext,
  evidence,
  fetchImpl,
  onEvidenceCapture,
  onNavigate,
}: MarginProfitabilityScreenProps) {
  const [marginState, setMarginState] = useState<MarginProfitabilityState>(() => evidence ? { kind: 'loaded', view: evidence } : { kind: 'loading' });
  const [showCompensationRefs, setShowCompensationRefs] = useState(false);
  const [exported, setExported] = useState('');

  useEffect(() => {
    if (evidence) {
      setMarginState({ kind: 'loaded', view: evidence });
      return;
    }

    let active = true;
    fetchMarginProfitability(tenantContext, fetchImpl)
      .then((view) => {
        if (active) setMarginState({ kind: 'loaded', view });
      })
      .catch((error: unknown) => {
        const message = error instanceof Error ? error.message : 'Margin profitability evidence is unavailable.';
        if (active) setMarginState({ kind: 'unreachable', message });
      });
    return () => {
      active = false;
    };
  }, [evidence, fetchImpl, tenantContext]);

  useEffect(() => {
    if (marginState.kind !== 'loaded') return;
    const refs = collectEvidenceRefs(marginState.view);
    onEvidenceCapture?.({ screenId: 'margin-profitability', state: stateForMarginProfitability(marginState.view), evidenceTarget, refs });
  }, [marginState, onEvidenceCapture]);

  if (marginState.kind === 'loading') {
    return <section className="panel" aria-labelledby="margin-heading"><h2 id="margin-heading">Margin Profitability</h2><p role="status">Loading margin profitability evidence...</p></section>;
  }

  if (marginState.kind === 'unreachable') {
    return (
      <section className="panel" aria-labelledby="margin-heading">
        <h2 id="margin-heading">Margin Profitability</h2>
        <div className="banner banner--blocked" role="alert">
          <strong>Margin profitability unavailable</strong>
          <span>{marginState.message}</span>
          <span>Connected margin-service evidence is required; the UI will not synthesize margin rows.</span>
        </div>
      </section>
    );
  }

  const view = marginState.view;
  const floorRows = floorEvidenceRows(view.floorEvidence);
  const compensationAllowed = view.compensationDetailsVisible;

  return (
    <>
      <section className="hero hero--admin" aria-labelledby="margin-title">
        <p className="eyebrow">Margin service - PII-24-S16</p>
        <h2 id="margin-title">Margin Profitability</h2>
        <p>
          Review company, branch, LO, investor, secondary market, floor evidence, approval, and replay records from the margin profitability service.
          Compensation visibility, approval actions, exception routes, and redactions are controlled by the backend response.
        </p>
      </section>

      <section className="panel" aria-labelledby="margin-heading">
        <div className="panel-heading-row">
          <div>
            <p className="eyebrow">Tenant context</p>
            <h2 id="margin-heading">Margin profitability workspace</h2>
          </div>
          <button type="button" onClick={() => setExported(exportMarginProfitabilityJson(view))}>Export evidence JSON</button>
        </div>
        <dl className="status-grid">
          <dt>Tenant</dt><dd>{displayText(view.tenantContext || tenantContext)}</dd>
          <dt>Status</dt><dd>{displayText(view.status ?? stateForMarginProfitability(view))}</dd>
          <dt>Compensation visibility</dt><dd>{compensationAllowed ? 'Permitted by backend response' : 'Not permitted by backend response'}</dd>
          <dt>Support reference</dt><dd><code>{view.uiTraceId}</code></dd>
          <dt>Replay hash</dt><dd><code>{view.replayHash}</code></dd>
          <dt>Evidence target</dt><dd><code>{evidenceTarget}</code></dd>
        </dl>
        <label className="toggle-row">
          <input type="checkbox" checked={showCompensationRefs} disabled={!compensationAllowed} onChange={(event) => setShowCompensationRefs(event.target.checked)} />
          Show compensation refs
        </label>
        {!compensationAllowed ? <p className="field-help">Compensation refs stay hidden because the backend response does not grant visibility.</p> : null}
        <div className={stateForMarginProfitability(view) === 'blocked' ? 'banner banner--blocked' : 'banner banner--info'} role={stateForMarginProfitability(view) === 'blocked' ? 'alert' : 'status'}>
          <strong>{displayText(view.dependencyStatus)}</strong>
          <span>{displayText(view.fallbackReason)}</span>
        </div>
        {exported ? <textarea aria-label="Exported margin profitability evidence" readOnly value={exported} /> : null}
      </section>

      <MarginSections sections={view.sections} showCompensationRefs={showCompensationRefs && compensationAllowed} />
      <FloorEvidence rows={floorRows} onNavigate={onNavigate} />
      <ApprovalStatus approval={view.approvalStatus} />
      <EvidenceRefs view={view} />
    </>
  );
}

export function stateForMarginProfitability(view: MarginProfitabilityView) {
  if ((view.status ?? '').toLowerCase().includes('blocked')) return 'blocked';
  if (view.sections.length === 0 && floorEvidenceRows(view.floorEvidence).length === 0) return 'blocked';
  if (view.sections.some((section) => section.permissionState === 'REDACTED' || section.permissionState === 'RESTRICTED')) return 'needs-attention';
  return 'ready';
}

export function exportMarginProfitabilityJson(view: MarginProfitabilityView) {
  return JSON.stringify({ evidenceTarget, exportedAt: 'local-preview', view }, null, 2);
}

function MarginSections({ sections, showCompensationRefs }: { sections: MarginEvidenceSection[]; showCompensationRefs: boolean }) {
  return (
    <section className="panel" aria-labelledby="margin-sections-heading">
      <div className="panel-heading-row"><div><p className="eyebrow">Margin Sections</p><h2 id="margin-sections-heading">Company, branch, LO, investor, and secondary market refs</h2></div></div>
      {sections.length === 0 ? <div className="banner banner--blocked" role="alert">No margin sections were supplied by the backend.</div> : (
        <div className="offer-list" role="list" aria-label="Margin sections">
          {sections.map((section) => <MarginSectionCard key={section.sectionId} section={section} showCompensationRefs={showCompensationRefs} />)}
        </div>
      )}
    </section>
  );
}

function MarginSectionCard({ section, showCompensationRefs }: { section: MarginEvidenceSection; showCompensationRefs: boolean }) {
  const restricted = section.permissionState === 'RESTRICTED';
  const redacted = section.permissionState === 'REDACTED' || restricted;
  return (
    <article className="offer-card" role="listitem" aria-label={`${section.label} evidence`}>
      <details open>
        <summary><strong>{displayText(section.label)}</strong> - {displayText(section.permissionState)}</summary>
        <dl className="status-grid">
          <dt>Source ref</dt><dd><code>{section.sourceRef}</code></dd>
          <dt>Permission state</dt><dd>{displayText(section.permissionState)}</dd>
          <dt>Display value</dt><dd>{redacted ? '[REDACTED]' : displayText(section.displayValue)}</dd>
        </dl>
        {restricted && section.requestAccessAllowed ? <button type="button" aria-label={`Request access to ${section.label}`}>Request Access</button> : null}
        {showCompensationRefs ? <p className="field-help">Compensation ref: <code>{section.sourceRef}</code></p> : null}
        <ExpandableRefList label={`${section.label} evidence refs`} values={section.evidenceRefs} emptyText="No evidence refs supplied." open />
        {section.redactions.length > 0 ? (
          <div className="banner banner--blocked" role="status" aria-label={`${section.label} redaction details`}>
            {section.redactions.map((redaction) => <span key={`${redaction.fieldLabel}-${redaction.auditRef}`}>[REDACTED] {displayText(redaction.fieldLabel)} - {displayText(redaction.reason)} - audit <code>{redaction.auditRef}</code></span>)}
          </div>
        ) : null}
      </details>
    </article>
  );
}

function FloorEvidence({ rows, onNavigate }: { rows: MarginFloorEvidence[]; onNavigate?: (target: string) => void }) {
  return (
    <section className="panel" aria-labelledby="margin-floor-heading">
      <div className="panel-heading-row"><div><p className="eyebrow">Floor Evidence</p><h2 id="margin-floor-heading">Quote option floor decisions and exception routes</h2></div></div>
      {rows.length === 0 ? <div className="banner banner--info" role="status">No floor policy configured</div> : (
        <div className="quote-table" role="table" aria-label="Floor evidence table">
          <div role="row" className="quote-table__row quote-table__row--head"><span role="columnheader">Quote option</span><span role="columnheader">Decision</span><span role="columnheader">Policy refs</span><span role="columnheader">Exception route</span><span role="columnheader">Guidance</span></div>
          {rows.map((row) => <FloorEvidenceRow key={row.quoteOptionId} row={row} onNavigate={onNavigate} />)}
        </div>
      )}
    </section>
  );
}

function FloorEvidenceRow({ row, onNavigate }: { row: MarginFloorEvidence; onNavigate?: (target: string) => void }) {
  const route = safeLocalRoute(row.exceptionRouteRef);
  return (
    <div role="row" className="quote-table__row">
      <span role="cell"><code>{row.quoteOptionId}</code></span>
      <span role="cell"><strong className={`decision-pill decision-pill--${row.decision.toLowerCase()}`}>{displayText(row.decision)}</strong><br />{displayText(row.decisionCode)}</span>
      <span role="cell">Policy <code>{row.floorPolicyVersionRef}</code><br />Threshold <code>{row.thresholdRef}</code><details><summary>Threshold calculation and workflow refs</summary><p>{displayText(row.thresholdCalculationRef)}</p><p>{displayText(row.exceptionWorkflowRef)}</p></details></span>
      <span role="cell">{route ? <button type="button" onClick={() => onNavigate?.(route)}>View Exception</button> : 'Exception route not configured'}<br /><code>{displayText(row.exceptionRouteRef)}</code></span>
      <span role="cell">{displayText(row.displayGuidance)}<ExpandableRefList label={`${row.quoteOptionId} audit refs`} values={row.auditRefs} emptyText="No audit refs supplied." /></span>
    </div>
  );
}

function ApprovalStatus({ approval }: { approval?: MarginApprovalView }) {
  return (
    <section className="panel" aria-labelledby="approval-heading">
      <div className="panel-heading-row"><div><p className="eyebrow">Approval Status</p><h2 id="approval-heading">Version, chain, actions, snapshots, and audit refs</h2></div></div>
      {!approval ? <div className="banner banner--info" role="status">No approval workflow supplied.</div> : (
        <>
          <dl className="status-grid"><dt>Current version</dt><dd><code>{approval.currentVersionRef}</code></dd><dt>Status</dt><dd>{displayText(approval.status)}</dd></dl>
          <div className="button-row" role="group" aria-label="Approval actions">
            {['REQUEST_APPROVAL', 'APPROVE', 'REJECT'].map((action) => <button key={action} type="button" disabled={!approval.allowedActions.includes(action)}>{approvalActionLabel(action)}</button>)}
          </div>
          <div className="quote-table" role="table" aria-label="Approval chain">
            <div role="row" className="quote-table__row quote-table__row--head"><span role="columnheader">Role</span><span role="columnheader">Actor</span><span role="columnheader">Status</span><span role="columnheader">Timestamp ref</span><span role="columnheader">Audit ref</span></div>
            {approval.chain.map((step) => <div key={`${step.role}-${step.auditRef}`} role="row" className="quote-table__row"><span role="cell">{displayText(step.role)}</span><span role="cell"><code>{step.actorRef}</code></span><span role="cell">{displayText(step.status)}</span><span role="cell"><code>{step.timestampRef}</code></span><span role="cell"><code>{step.auditRef}</code></span></div>)}
          </div>
          <ExpandableRefList label="Approval snapshot refs" values={approval.snapshotRefs} emptyText="No snapshot refs supplied." open />
          <ExpandableRefList label="Approval audit refs" values={approval.auditRefs} emptyText="No audit refs supplied." open />
        </>
      )}
    </section>
  );
}

function EvidenceRefs({ view }: { view: MarginProfitabilityView }) {
  return (
    <section className="panel" aria-labelledby="margin-evidence-heading">
      <div className="panel-heading-row"><div><p className="eyebrow">Evidence Refs</p><h2 id="margin-evidence-heading">Version refs, audit refs, replay hash, and correlation refs</h2></div></div>
      <dl className="status-grid"><dt>Replay hash</dt><dd><code>{view.replayHash}</code></dd><dt>Support reference</dt><dd><code>{view.uiTraceId}</code></dd></dl>
      <ExpandableRefList label="Version refs" values={view.versionRefs} emptyText="No version refs supplied." open />
      <ExpandableRefList label="Audit refs" values={view.auditRefs} emptyText="No audit refs supplied." open />
      <ExpandableRefList label="Correlation refs" values={view.correlationRefs ?? []} emptyText="No correlation refs supplied." />
      <ExpandableRefList label="Margin events" values={view.events} emptyText="No events supplied." />
      <p><a href={`/audit/replay?hash=${encodeURIComponent(view.replayHash)}`}>Open audit replay</a> · <a href="/admin/governance">Open governance config</a></p>
    </section>
  );
}

function floorEvidenceRows(floorEvidence: MarginProfitabilityView['floorEvidence']) {
  return Array.isArray(floorEvidence) ? floorEvidence : [floorEvidence].filter(Boolean);
}

function collectEvidenceRefs(view: MarginProfitabilityView) {
  return [
    view.replayHash,
    ...view.versionRefs,
    ...view.auditRefs,
    ...(view.correlationRefs ?? []),
    ...view.sections.flatMap((section) => [...section.evidenceRefs, ...section.redactions.map((redaction) => redaction.auditRef)]),
    ...floorEvidenceRows(view.floorEvidence).flatMap((row) => [row.floorPolicyVersionRef, row.thresholdRef, row.exceptionRouteRef, ...row.auditRefs]),
    ...(view.approvalStatus ? [view.approvalStatus.currentVersionRef, ...view.approvalStatus.snapshotRefs, ...view.approvalStatus.auditRefs] : []),
  ].filter(Boolean);
}

function ExpandableRefList({ label, values, emptyText, open = false }: { label: string; values: string[]; emptyText: string; open?: boolean }) {
  return (
    <details className="blocker-details" open={open}>
      <summary>{label}</summary>
      {values.length === 0 ? <p>{emptyText}</p> : <ul>{values.map((value) => <li key={value}><code>{value}</code></li>)}</ul>}
    </details>
  );
}

function safeLocalRoute(route: string) {
  return route.startsWith('/') && !route.startsWith('//') ? route : '';
}

function displayText(value: string | number | boolean | null | undefined) {
  if (value === null || value === undefined || value === '') return 'Not supplied';
  return String(value).replace(/[_-]+/g, ' ');
}

function approvalActionLabel(action: string) {
  if (action === 'REQUEST_APPROVAL') return 'Request Approval';
  if (action === 'APPROVE') return 'Approve';
  if (action === 'REJECT') return 'Reject';
  return displayText(action);
}
