import { useEffect, useState } from 'react';
import {
  fetchPerformanceDashboard,
  type PerformanceBlocker,
  type PerformanceDashboardView,
  type PerformanceImpact,
  type PerformanceSignal,
  type PerformanceSignalGroup,
} from '../../lib/api/observabilityPerformance';

type PerformanceState =
  | { kind: 'loading' }
  | { kind: 'loaded'; view: PerformanceDashboardView }
  | { kind: 'unreachable'; message: string };

type PerformanceDashboardScreenProps = {
  tenantContext?: string;
  evidence?: PerformanceDashboardView;
  fetchImpl?: typeof fetch;
  onEvidenceCapture?: (evidence: { screenId: string; state: string; evidenceTarget: string; refs: string[] }) => void;
  onNavigate?: (target: string) => void;
};

type EvidenceLink = {
  service: string;
  type: string;
  label: string;
  url: string;
};

const evidenceTarget = '.local-harness/evidence/PII-24-S20/performance-dashboard.json';
const defaultTenantContext = 'ui-preview-tenant';
const timeRanges = ['1h', '6h', '24h', '7d'] as const;
const autoRefreshSeconds = 15;

export default function PerformanceDashboardScreen({
  tenantContext = defaultTenantContext,
  evidence,
  fetchImpl,
  onEvidenceCapture,
  onNavigate,
}: PerformanceDashboardScreenProps) {
  const [performanceState, setPerformanceState] = useState<PerformanceState>(() => evidence ? { kind: 'loaded', view: evidence } : { kind: 'loading' });
  const [timeRange, setTimeRange] = useState<(typeof timeRanges)[number]>('1h');

  useEffect(() => {
    if (evidence) {
      setPerformanceState({ kind: 'loaded', view: evidence });
      return;
    }

    let active = true;
    fetchPerformanceDashboard(tenantContext, fetchImpl)
      .then((view) => {
        if (active) setPerformanceState({ kind: 'loaded', view });
      })
      .catch((error: unknown) => {
        const message = error instanceof Error ? error.message : 'Performance dashboard is unavailable.';
        if (active) setPerformanceState({ kind: 'unreachable', message });
      });
    return () => {
      active = false;
    };
  }, [evidence, fetchImpl, tenantContext]);

  useEffect(() => {
    if (performanceState.kind !== 'loaded') return;
    onEvidenceCapture?.({
      screenId: 'performance-dashboard',
      state: stateForPerformanceDashboard(performanceState.view),
      evidenceTarget,
      refs: collectPerformanceEvidenceRefs(performanceState.view),
    });
  }, [performanceState, onEvidenceCapture]);

  if (performanceState.kind === 'loading') {
    return <section className="panel" aria-labelledby="performance-heading"><h2 id="performance-heading">Performance Dashboard</h2><p role="status">Loading performance dashboard...</p></section>;
  }

  if (performanceState.kind === 'unreachable') {
    return (
      <section className="panel" aria-labelledby="performance-heading">
        <h2 id="performance-heading">Performance Dashboard</h2>
        <div className="banner banner--blocked" role="alert">
          <strong>Performance dashboard unavailable</strong>
          <span>{performanceState.message}</span>
          <span>Connected observability-service refs are required; the UI will not synthesize metrics, thresholds, or alerting rules.</span>
        </div>
      </section>
    );
  }

  const view = performanceState.view;
  const state = stateForPerformanceDashboard(view);
  const evidenceLinks = parseEvidenceLinks(view.evidenceLinks);

  return (
    <>
      <section className="hero hero--performance" aria-labelledby="performance-title">
        <p className="eyebrow">Observability - PII-24-S20</p>
        <h2 id="performance-title">Performance Dashboard</h2>
        <p>
          Inspect service health signal groups, freshness, impacts, evidence links, and blockers from backend-owned refs. The browser
          displays operational evidence only and does not collect metrics, define alert thresholds, or change runtime configuration.
        </p>
      </section>

      <section className="panel performance-dashboard-sticky" aria-labelledby="performance-heading">
        <div className="panel-heading-row">
          <div>
            <p className="eyebrow">Tenant context</p>
            <h2 id="performance-heading">Performance dashboard workspace</h2>
          </div>
          <button type="button" disabled aria-describedby="performance-refresh-disabled">Refresh</button>
        </div>
        <p id="performance-refresh-disabled" className="field-help">Auto-refresh is represented at {autoRefreshSeconds}s; live polling remains disabled until the backend cache contract is available.</p>
        <label>Time range
          <select value={timeRange} onChange={(event) => setTimeRange(event.target.value as (typeof timeRanges)[number])}>
            {timeRanges.map((range) => <option key={range} value={range}>{range}</option>)}
          </select>
        </label>
        <dl className="status-grid">
          <dt>Tenant</dt><dd>{displayText(view.tenantContext || tenantContext)}</dd>
          <dt>Status</dt><dd>{displayText(view.dependencyStatus)}</dd>
          <dt>Support reference</dt><dd><code>{view.uiTraceId}</code></dd>
          <dt>Selected range</dt><dd><code>{timeRange}</code></dd>
          <dt>Evidence target</dt><dd><code>{evidenceTarget}</code></dd>
        </dl>
        <div className={state === 'blocked' ? 'banner banner--blocked' : 'banner banner--info'} role={state === 'blocked' ? 'alert' : 'status'}>
          <strong>{displayText(view.dependencyStatus)}</strong>
          <span>{displayText(view.fallbackReason)}</span>
          <span>Action policy: {view.actionsDisabled ? 'backend actions disabled or fixture-backed' : 'backend action contract supplied'}</span>
        </div>
      </section>

      <SignalGroups groups={view.signalGroups} onEvidence={(refs) => onNavigate?.(evidenceTargetForRefs(refs, evidenceLinks))} />
      <Impacts impacts={view.impacts} actionsDisabled={view.actionsDisabled} onNavigate={onNavigate} />
      <EvidenceLinks links={evidenceLinks} />
      <Blockers blockers={view.blockers} actionsDisabled={view.actionsDisabled} onNavigate={onNavigate} />

      <section className="panel" aria-labelledby="performance-events-heading">
        <h2 id="performance-events-heading">Events and unavailable workflow gaps</h2>
        <ExpandableRefList label="Performance dashboard events" values={view.events} emptyText="No performance events supplied." open />
        <ExpandableRefList label="Known unavailable integration gaps" values={['live observability-service action workflow', 'metrics collection', 'alerting rule configuration', 'Storybook visual states', 'E2E evidence navigation']} emptyText="No blocked gaps recorded." open />
      </section>
    </>
  );
}

export function stateForPerformanceDashboard(view: PerformanceDashboardView) {
  if (view.dependencyStatus.toLowerCase().includes('blocked')) return 'blocked';
  if (view.signalGroups.length === 0 || view.blockers.length > 0 || view.signalGroups.some((group) => group.blockers.length > 0)) return 'needs-attention';
  return 'ready';
}

export function exportPerformanceDashboardJson(view: PerformanceDashboardView) {
  return JSON.stringify({ evidenceTarget, exportedAt: 'local-preview', view }, null, 2);
}

function SignalGroups({ groups, onEvidence }: { groups: PerformanceSignalGroup[]; onEvidence: (refs: string[]) => void }) {
  return (
    <section className="panel" aria-labelledby="signal-groups-heading">
      <div className="panel-heading-row"><div><p className="eyebrow">Signal groups</p><h2 id="signal-groups-heading">Service health signals by freshness</h2></div></div>
      {groups.length === 0 ? <div className="banner banner--blocked" role="alert">No signal groups were supplied by observability-service.</div> : groups.map((group) => <SignalGroup key={`${group.serviceName}-${group.correlationId}`} group={group} onEvidence={onEvidence} />)}
    </section>
  );
}

function SignalGroup({ group, onEvidence }: { group: PerformanceSignalGroup; onEvidence: (refs: string[]) => void }) {
  return (
    <details className="blocker-details" open>
      <summary><strong>{displayText(group.serviceName)}</strong> <FreshnessBadge freshness={group.freshness} /></summary>
      <dl className="status-grid">
        <dt>Tenant</dt><dd>{displayText(group.tenantContext)}</dd>
        <dt>Correlation ID</dt><dd><code>{group.correlationId}</code></dd>
        <dt>Freshness</dt><dd>{displayText(group.freshness)}</dd>
      </dl>
      {group.signals.length === 0 ? <p role="status">No signals for this service</p> : <SignalTable signals={group.signals} onEvidence={onEvidence} />}
      <Blockers blockers={group.blockers} actionsDisabled onNavigate={() => undefined} compact />
    </details>
  );
}

function SignalTable({ signals, onEvidence }: { signals: PerformanceSignal[]; onEvidence: (refs: string[]) => void }) {
  return (
    <div className="quote-table" role="table" aria-label="Performance signals">
      <div role="row" className="quote-table__row quote-table__row--head"><span role="columnheader">Signal</span><span role="columnheader">Freshness</span><span role="columnheader">Source</span><span role="columnheader">Source ref</span><span role="columnheader">Evidence refs</span><span role="columnheader">Action</span></div>
      {signals.map((signal) => (
        <div key={signal.signalId} role="row" className="quote-table__row">
          <span role="cell"><strong>{displayText(signal.label)}</strong></span>
          <span role="cell"><FreshnessBadge freshness={signal.freshness} /></span>
          <span role="cell">{displayText(signal.source)}</span>
          <span role="cell"><code>{signal.sourceRef}</code></span>
          <span role="cell"><ExpandableRefList label={`${signal.label} evidence refs`} values={signal.evidenceRefs} emptyText="No evidence refs supplied." /></span>
          <span role="cell"><button type="button" disabled={signal.evidenceRefs.length === 0} onClick={() => onEvidence(signal.evidenceRefs)}>View Evidence</button></span>
        </div>
      ))}
    </div>
  );
}

function Impacts({ impacts, actionsDisabled, onNavigate }: { impacts: PerformanceImpact[]; actionsDisabled: boolean; onNavigate?: (target: string) => void }) {
  return (
    <section className="panel" aria-labelledby="performance-impact-heading">
      <h2 id="performance-impact-heading">Impacts, severity, and recovery ownership</h2>
      <div className="quote-table" role="table" aria-label="Performance impacts">
        <div role="row" className="quote-table__row quote-table__row--head"><span role="columnheader">Impact Code</span><span role="columnheader">Severity</span><span role="columnheader">Summary</span><span role="columnheader">Source</span><span role="columnheader">Recovery Owner</span><span role="columnheader">Runbook Ref</span><span role="columnheader">Actions</span></div>
        {impacts.length === 0 ? <div role="row" className="quote-table__row"><span role="cell">No impacts supplied by observability-service.</span></div> : impacts.map((impact) => <ImpactRow key={impact.impactCode} impact={impact} actionsDisabled={actionsDisabled} onNavigate={onNavigate} />)}
      </div>
    </section>
  );
}

function ImpactRow({ impact, actionsDisabled, onNavigate }: { impact: PerformanceImpact; actionsDisabled: boolean; onNavigate?: (target: string) => void }) {
  const runbookRoute = safeLocalRoute(impact.runbookRef);
  return (
    <div role="row" className="quote-table__row">
      <span role="cell"><code>{impact.impactCode}</code></span>
      <span role="cell"><SeverityBadge severity={impact.severity ?? 'LOW'} /></span>
      <span role="cell">{impact.summary}</span>
      <span role="cell">{displayText(impact.source)}</span>
      <span role="cell">{impact.recoveryOwner || 'Unassigned'}</span>
      <span role="cell">{impact.runbookRef ? <code>{impact.runbookRef}</code> : 'Runbook not configured'}</span>
      <span role="cell"><button type="button" disabled={!runbookRoute} onClick={() => runbookRoute && onNavigate?.(runbookRoute)}>View Runbook</button><button type="button" disabled={actionsDisabled}>Assign to Me</button><button type="button" onClick={() => onNavigate?.('/ops/cases')} disabled={actionsDisabled}>Create Case</button></span>
    </div>
  );
}

function EvidenceLinks({ links }: { links: EvidenceLink[] }) {
  const services = uniqueValues(links.map((link) => link.service));
  return (
    <section className="panel" aria-labelledby="evidence-links-heading">
      <h2 id="evidence-links-heading">Evidence links grouped by service</h2>
      {services.length === 0 ? <p role="status">No evidence links supplied.</p> : services.map((service) => (
        <article key={service} className="module-card module-card--light">
          <h3>{displayText(service)}</h3>
          <ul className="offer-list" aria-label={`${service} evidence links`}>
            {links.filter((link) => link.service === service).map((link) => (
              <li key={`${link.service}-${link.label}-${link.url}`}>
                <strong>{link.label}</strong> <code>{link.type}</code> <a href={link.url} target="_blank" rel="noreferrer">Open in New Tab</a>
              </li>
            ))}
          </ul>
        </article>
      ))}
    </section>
  );
}

function Blockers({ blockers, actionsDisabled, onNavigate, compact = false }: { blockers: PerformanceBlocker[]; actionsDisabled: boolean; onNavigate?: (target: string) => void; compact?: boolean }) {
  if (blockers.length === 0) return compact ? null : <section className="panel" aria-labelledby="blockers-heading"><h2 id="blockers-heading">Blockers</h2><p role="status">No blockers supplied.</p></section>;
  const content = (
    <div className="quote-table" role="table" aria-label="Performance blockers">
      <div role="row" className="quote-table__row quote-table__row--head"><span role="columnheader">Code</span><span role="columnheader">Owner</span><span role="columnheader">Message</span><span role="columnheader">Actions</span></div>
      {blockers.map((blocker) => (
        <div key={`${blocker.code}-${blocker.owner}`} role="row" className="quote-table__row">
          <span role="cell"><code>{blocker.code}</code></span>
          <span role="cell">{blocker.owner || 'Unassigned'}</span>
          <span role="cell">{blocker.message}</span>
          <span role="cell"><button type="button" disabled={!blocker.caseId} onClick={() => blocker.caseId && onNavigate?.(`/ops/cases/${encodeURIComponent(blocker.caseId)}`)}>View in Ops Cases</button><button type="button" disabled={actionsDisabled}>Assign Recovery Owner</button></span>
        </div>
      ))}
    </div>
  );
  return compact ? content : <section className="panel" aria-labelledby="blockers-heading"><h2 id="blockers-heading">Blockers</h2>{content}</section>;
}

function FreshnessBadge({ freshness }: { freshness: string }) {
  const normalized = freshness.toUpperCase();
  const className = normalized === 'LIVE' ? 'decision-pill decision-pill--eligible' : normalized === 'STALE' ? 'decision-pill decision-pill--conditional' : 'decision-pill decision-pill--ineligible';
  return <span className={className}>{displayText(freshness)}</span>;
}

function SeverityBadge({ severity }: { severity: string }) {
  const normalized = severity.toUpperCase();
  const className = normalized === 'LOW' ? 'decision-pill decision-pill--eligible' : normalized === 'MEDIUM' ? 'decision-pill decision-pill--conditional' : 'decision-pill decision-pill--ineligible';
  return <span className={className}>{displayText(severity)}</span>;
}

function ExpandableRefList({ label, values, emptyText, open = false }: { label: string; values: string[]; emptyText: string; open?: boolean }) {
  return <details className="blocker-details" open={open}><summary>{label}</summary>{values.length === 0 ? <p>{emptyText}</p> : <ul>{values.map((value) => <li key={value}><code>{value}</code></li>)}</ul>}</details>;
}

function parseEvidenceLinks(values: string[]): EvidenceLink[] {
  return values.map((value) => {
    const [service, type, label, url] = value.split('|');
    return { service: service || 'unassigned-service', type: type || 'REF', label: label || value, url: safeLocalRoute(url || '') || '#' };
  });
}

function evidenceTargetForRefs(refs: string[], links: EvidenceLink[]) {
  const match = links.find((link) => refs.some((ref) => link.url.includes(ref) || link.label.includes(ref)));
  return match?.url ?? '#';
}

function collectPerformanceEvidenceRefs(view: PerformanceDashboardView) {
  return uniqueValues([
    view.uiTraceId,
    evidenceTarget,
    ...view.evidenceLinks,
    ...view.signalGroups.flatMap((group) => [group.serviceName, group.correlationId, ...group.signals.flatMap((signal) => [signal.sourceRef, ...signal.evidenceRefs]), ...group.blockers.flatMap((blocker) => [blocker.code, blocker.caseId ?? ''])]),
    ...view.impacts.flatMap((impact) => [impact.impactCode, impact.source, impact.runbookRef]),
    ...view.blockers.flatMap((blocker) => [blocker.code, blocker.caseId ?? '']),
    ...view.events,
  ]);
}

function safeLocalRoute(route: string) {
  return route.startsWith('/') && !route.startsWith('//') ? route : '';
}

function uniqueValues(values: string[]) {
  return Array.from(new Set(values.filter(Boolean))).sort((a, b) => a.localeCompare(b));
}

function displayText(value: string | number | boolean | null | undefined) {
  if (value === null || value === undefined || value === '') return 'Not supplied';
  return String(value).replace(/[._/-]+/g, ' ').replace(/\b\w/g, (letter) => letter.toUpperCase());
}
