import { useEffect, useState } from 'react';
import type React from 'react';
import {
  fetchComplianceEvidenceRegistry,
  type ComplianceAdvisoryReview,
  type ComplianceAlertSummary,
  type ComplianceDecisionRationale,
  type ComplianceEvidenceArtifact,
  type ComplianceEvidenceRegistryView,
  type FairLendingMonitoringDrilldown,
  type PrivacyRequestSummary,
  type RetentionControlSummary,
  type SecurityEventSummary,
} from '../../lib/api/complianceEvidence';

type ComplianceEvidenceState =
  | { kind: 'loading' }
  | { kind: 'loaded'; view: ComplianceEvidenceRegistryView }
  | { kind: 'unreachable'; message: string };

type ComplianceTab = 'artifacts' | 'decisions' | 'advisory' | 'fair-lending' | 'privacy' | 'security' | 'alerts' | 'retention' | 'config-gaps';

type ComplianceEvidenceScreenProps = {
  tenantContext?: string;
  evidence?: ComplianceEvidenceRegistryView;
  fetchImpl?: typeof fetch;
  onEvidenceCapture?: (evidence: { screenId: string; state: string; evidenceTarget: string; refs: string[] }) => void;
  onNavigate?: (target: string) => void;
};

const evidenceTarget = '.local-harness/evidence/PII-24-S28/compliance-evidence-registry.json';
const defaultTenantContext = 'ui-preview-tenant';
const tabs: { id: ComplianceTab; label: string }[] = [
  { id: 'artifacts', label: 'Artifacts' },
  { id: 'decisions', label: 'Decisions' },
  { id: 'advisory', label: 'Advisory Reviews' },
  { id: 'fair-lending', label: 'Fair Lending' },
  { id: 'privacy', label: 'Privacy Requests' },
  { id: 'security', label: 'Security Events' },
  { id: 'alerts', label: 'Alerts' },
  { id: 'retention', label: 'Retention Controls' },
  { id: 'config-gaps', label: 'Configuration Gaps' },
];

export default function ComplianceEvidenceScreen({ tenantContext = defaultTenantContext, evidence, fetchImpl, onEvidenceCapture, onNavigate }: ComplianceEvidenceScreenProps) {
  const [registryState, setRegistryState] = useState<ComplianceEvidenceState>(() => evidence ? { kind: 'loaded', view: evidence } : { kind: 'loading' });
  const [activeTab, setActiveTab] = useState<ComplianceTab>('artifacts');
  const [artifactTypeFilter, setArtifactTypeFilter] = useState('all');
  const [progressionBlockedOnly, setProgressionBlockedOnly] = useState(false);
  const [selectedArtifactId, setSelectedArtifactId] = useState<string | null>(null);
  const [processRequestId, setProcessRequestId] = useState<string | null>(null);
  const [legalHoldRuleId, setLegalHoldRuleId] = useState<string | null>(null);
  const [exported, setExported] = useState('');

  useEffect(() => {
    if (evidence) {
      setRegistryState({ kind: 'loaded', view: evidence });
      return;
    }

    let active = true;
    fetchComplianceEvidenceRegistry(fetchImpl)
      .then((view) => {
        if (active) setRegistryState({ kind: 'loaded', view });
      })
      .catch((error: unknown) => {
        const message = error instanceof Error ? error.message : 'Compliance evidence is unavailable.';
        if (active) setRegistryState({ kind: 'unreachable', message });
      });
    return () => {
      active = false;
    };
  }, [evidence, fetchImpl]);

  useEffect(() => {
    if (registryState.kind !== 'loaded') return;
    onEvidenceCapture?.({ screenId: 'compliance-evidence-registry', state: stateForComplianceEvidence(registryState.view), evidenceTarget, refs: registryRefs(registryState.view) });
  }, [registryState, onEvidenceCapture]);

  if (registryState.kind === 'loading') {
    return <section className="panel" aria-labelledby="compliance-heading"><h2 id="compliance-heading">Compliance Evidence Registry</h2><p role="status">Loading compliance evidence...</p></section>;
  }

  if (registryState.kind === 'unreachable') {
    return <section className="panel" aria-labelledby="compliance-heading"><h2 id="compliance-heading">Compliance Evidence Registry</h2><div className="banner banner--blocked" role="alert"><strong>Compliance evidence unavailable</strong><span>{registryState.message}</span><span>Connected compliance-service evidence is required; the UI will not synthesize compliance decisions.</span></div></section>;
  }

  const view = registryState.view;
  const registryStateName = stateForComplianceEvidence(view);
  const artifactTypes = uniqueValues(view.artifacts.map((artifact) => artifact.artifactType));
  const filteredArtifacts = view.artifacts.filter((artifact) => (artifactTypeFilter === 'all' || artifact.artifactType === artifactTypeFilter) && (!progressionBlockedOnly || artifact.progressionBlocked));
  const activeArtifact = view.artifacts.find((artifact) => artifact.artifactId === selectedArtifactId) ?? null;
  const processRequest = view.privacyRequests.find((request) => request.requestId === processRequestId) ?? null;
  const legalHoldRule = view.retentionControls.find((rule) => rule.ruleId === legalHoldRuleId) ?? null;

  return (
    <>
      <section className="hero hero--compliance" aria-labelledby="compliance-title">
        <p className="eyebrow">Compliance evidence - PII-24-S28</p>
        <h2 id="compliance-title">Compliance Evidence Registry</h2>
        <p>Centralized audit evidence for artifacts, decisions, advisory reviews, fair lending, privacy requests, security events, alerts, retention controls, and configuration gaps. The screen renders configured refs only.</p>
      </section>
      <section className="panel" aria-labelledby="compliance-heading">
        <div className="panel-heading-row"><div><p className="eyebrow">Tenant context</p><h2 id="compliance-heading">Compliance evidence workspace</h2></div><button type="button" onClick={() => setExported(exportComplianceEvidenceJson(view))}>Export Evidence</button></div>
        <dl className="status-grid"><dt>Tenant</dt><dd>{displayText(view.tenantContext || tenantContext)}</dd><dt>Registry state</dt><dd>{displayText(registryStateName)}</dd><dt>Dependency</dt><dd>{displayText(view.dependencyStatus)}</dd><dt>Trace ID</dt><dd><code>{view.uiTraceId}</code></dd><dt>Evidence target</dt><dd><code>{evidenceTarget}</code></dd></dl>
        <div className={registryStateName === 'blocked' ? 'banner banner--blocked' : 'banner banner--info'} role={registryStateName === 'blocked' ? 'alert' : 'status'}><strong>{displayText(view.dependencyStatus)}</strong><span>{displayText(view.fallbackReason || 'Compliance evidence is ready.')}</span><span>No compliance rules, retention calculations, or regulatory decisions are computed in this UI.</span></div>
        <nav className="custom-rules-shell__nav" aria-label="Compliance evidence tabs">{tabs.map((tab) => <button key={tab.id} type="button" aria-pressed={activeTab === tab.id} onClick={() => setActiveTab(tab.id)}>{tab.label}</button>)}</nav>
        {exported ? <textarea aria-label="Exported compliance evidence" readOnly value={exported} /> : null}
      </section>
      {activeTab === 'artifacts' ? <ArtifactsPanel artifacts={filteredArtifacts} artifactTypes={artifactTypes} artifactTypeFilter={artifactTypeFilter} progressionBlockedOnly={progressionBlockedOnly} setArtifactTypeFilter={setArtifactTypeFilter} setProgressionBlockedOnly={setProgressionBlockedOnly} onSelectArtifact={setSelectedArtifactId} /> : null}
      {activeTab === 'decisions' ? <DecisionsPanel decisions={view.decisions} onNavigate={onNavigate} /> : null}
      {activeTab === 'advisory' ? <AdvisoryPanel reviews={view.advisoryReviews} onNavigate={onNavigate} /> : null}
      {activeTab === 'fair-lending' ? <FairLendingPanel drilldowns={view.fairLendingMonitoring} onNavigate={onNavigate} /> : null}
      {activeTab === 'privacy' ? <PrivacyPanel requests={view.privacyRequests} onProcess={setProcessRequestId} /> : null}
      {activeTab === 'security' ? <SecurityPanel events={view.securityEvents} /> : null}
      {activeTab === 'alerts' ? <AlertsPanel alerts={view.alerts} onNavigate={onNavigate} /> : null}
      {activeTab === 'retention' ? <RetentionPanel controls={view.retentionControls} onLegalHold={setLegalHoldRuleId} /> : null}
      {activeTab === 'config-gaps' ? <ConfigGapsPanel gaps={view.configurationGaps} onNavigate={onNavigate} /> : null}
      {activeArtifact ? <ArtifactModal artifact={activeArtifact} onClose={() => setSelectedArtifactId(null)} /> : null}
      {processRequest ? <ProcessRequestModal request={processRequest} onClose={() => setProcessRequestId(null)} /> : null}
      {legalHoldRule ? <LegalHoldModal control={legalHoldRule} onClose={() => setLegalHoldRuleId(null)} /> : null}
    </>
  );
}

export function stateForComplianceEvidence(view: ComplianceEvidenceRegistryView) {
  const rowCount = view.artifacts.length + view.decisions.length + view.advisoryReviews.length + view.fairLendingMonitoring.length + view.privacyRequests.length + view.securityEvents.length + view.alerts.length + view.retentionControls.length + view.configurationGaps.length;
  if (view.configurationGaps.length > 0 || view.artifacts.some((artifact) => artifact.progressionBlocked) || view.advisoryReviews.some((review) => review.blockedByConfiguration)) return 'blocked';
  return rowCount === 0 ? 'empty' : 'ready';
}

export function exportComplianceEvidenceJson(view: ComplianceEvidenceRegistryView) {
  return JSON.stringify({ evidenceTarget, exportedAt: 'local-preview', view }, null, 2);
}

function ArtifactsPanel({ artifacts, artifactTypes, artifactTypeFilter, progressionBlockedOnly, setArtifactTypeFilter, setProgressionBlockedOnly, onSelectArtifact }: { artifacts: ComplianceEvidenceArtifact[]; artifactTypes: string[]; artifactTypeFilter: string; progressionBlockedOnly: boolean; setArtifactTypeFilter: (value: string) => void; setProgressionBlockedOnly: (value: boolean) => void; onSelectArtifact: (artifactId: string) => void }) {
  return <section className="panel" aria-labelledby="artifacts-heading"><div className="panel-heading-row"><div><p className="eyebrow">Artifacts</p><h2 id="artifacts-heading">Artifact traceability table</h2></div></div><div className="intake-form" role="group" aria-label="Artifact filters"><label>Type<select value={artifactTypeFilter} onChange={(event) => setArtifactTypeFilter(event.target.value)}><option value="all">All types</option>{artifactTypes.map((value) => <option key={value} value={value}>{displayText(value)}</option>)}</select></label><label><input type="checkbox" checked={progressionBlockedOnly} onChange={(event) => setProgressionBlockedOnly(event.target.checked)} /> Progression blocked only</label></div><div className="quote-table" role="table" aria-label="Compliance evidence artifacts"><div role="row" className="quote-table__row quote-table__row--head"><span role="columnheader">Artifact ID</span><span role="columnheader">Path and type</span><span role="columnheader">Owner and retention</span><span role="columnheader">Version and hash</span><span role="columnheader">Policy and jurisdiction</span><span role="columnheader">Continuity and blockers</span><span role="columnheader">Actions</span></div>{artifacts.length === 0 ? <div role="row" className="quote-table__row"><span role="cell">No compliance artifacts match the current filters.</span></div> : artifacts.map((artifact) => <div key={artifact.artifactId} role="row" className="quote-table__row"><span role="cell"><code>{artifact.artifactId}</code></span><span role="cell">{displayText(artifact.path)}<br />{artifact.artifactType}</span><span role="cell">{displayText(artifact.owner)}<br />{artifact.retentionClass}<br />{displayText(artifact.relatedModule)}</span><span role="cell"><code>{artifact.version}</code><br /><code>{artifact.hash}</code><br /><code>{artifact.traceId}</code></span><span role="cell"><code>{artifact.policyVersion}</code><br /><code>{artifact.policyDigest}</code><br />{displayText(artifact.jurisdictionCode)}</span><span role="cell">{artifact.continuityStatus}<br />Progression blocked: {artifact.progressionBlocked ? 'yes' : 'no'}<ExpandableRefList label={`${artifact.artifactId} blockers`} values={artifact.blockers} emptyText="No artifact blockers." /><ExpandableRefList label={`${artifact.artifactId} module links`} values={artifact.moduleLinks} emptyText="No module links supplied." /></span><span role="cell"><button type="button" onClick={() => onSelectArtifact(artifact.artifactId)}>View Artifact</button><br /><button type="button" aria-label={`Export ${artifact.artifactId} with redaction profile`}>Export Artifact</button></span></div>)}</div></section>;
}

function DecisionsPanel({ decisions, onNavigate }: { decisions: ComplianceDecisionRationale[]; onNavigate?: (target: string) => void }) {
  return <section className="panel" aria-labelledby="decisions-heading"><h2 id="decisions-heading">Decision rationale and disclosure artifacts</h2><div className="quote-table" role="table" aria-label="Compliance decisions"><div role="row" className="quote-table__row quote-table__row--head"><span role="columnheader">Decision ID</span><span role="columnheader">Reason code</span><span role="columnheader">Human text</span><span role="columnheader">Jurisdiction</span><span role="columnheader">Reason tiers</span><span role="columnheader">Export blocked</span><span role="columnheader">Disclosure artifact</span></div>{decisions.length === 0 ? <div role="row" className="quote-table__row"><span role="cell">No compliance decisions supplied.</span></div> : decisions.map((decision) => <div key={decision.decisionId} role="row" className="quote-table__row"><span role="cell"><code>{decision.decisionId}</code></span><span role="cell">{decision.reasonCode}</span><span role="cell">{decision.humanText}<br /><details><summary>Full rationale</summary><p>{decision.humanText}</p><p>Regulatory citation: configured-service-ref-required</p></details></span><span role="cell">{displayText(decision.jurisdictionCode)}</span><span role="cell"><ExpandableRefList label={`${decision.decisionId} reason tiers`} values={decision.reasonTiers} emptyText="No reason tiers supplied." /></span><span role="cell">{decision.exportBlocked ? 'yes' : 'no'}</span><span role="cell"><button type="button" onClick={() => onNavigate?.(`/compliance/evidence?artifact=${encodeURIComponent(decision.disclosureArtifactRef)}`)}>View Disclosure Artifact</button><br /><code>{decision.disclosureArtifactRef}</code></span></div>)}</div></section>;
}

function AdvisoryPanel({ reviews, onNavigate }: { reviews: ComplianceAdvisoryReview[]; onNavigate?: (target: string) => void }) {
  return <CardList title="Advisory reviews" items={reviews.map((review) => ({ id: review.reviewId, title: `${displayText(review.reviewType)} - ${displayText(review.status)}`, body: [`Subject: ${displayText(review.subjectRef)}`, `Regulatory approval: ${displayText(review.regulatoryApprovalState)}`, `Blocked by config: ${review.blockedByConfiguration ? 'yes' : 'no'}`], refs: [...review.reasonCodes, ...review.auditSnapshotRefs, ...review.exportRefs, ...review.configurationGaps], actions: <><button type="button" onClick={() => onNavigate?.(`/audit/replay?snapshot=${encodeURIComponent(review.auditSnapshotRefs[0] ?? review.reviewId)}`)}>View Audit Snapshot</button><button type="button" aria-label={`Export review ${review.reviewId}`}>Export Review</button></> }))} emptyText="No advisory reviews supplied." />;
}

function FairLendingPanel({ drilldowns, onNavigate }: { drilldowns: FairLendingMonitoringDrilldown[]; onNavigate?: (target: string) => void }) {
  return <CardList title="Fair lending drilldowns" items={drilldowns.map((drilldown) => ({ id: drilldown.drilldownId, title: drilldown.drilldownId, body: [`Redacted: ${drilldown.redacted ? 'yes' : 'no'}`, `Redaction state: ${displayText(drilldown.redactionState)}`, `Dimensions: ${drilldown.dimensions.map(displayText).join(', ')}`], refs: [...drilldown.evidenceRefs, ...drilldown.blockers], actions: <><button type="button" onClick={() => onNavigate?.(`/compliance/evidence?evidence=${encodeURIComponent(drilldown.evidenceRefs[0] ?? drilldown.drilldownId)}`)}>View Evidence</button><button type="button" aria-label={`Export drilldown ${drilldown.drilldownId} with redaction`}>Export Drilldown</button></> }))} emptyText="No fair lending drilldowns supplied." />;
}

function PrivacyPanel({ requests, onProcess }: { requests: PrivacyRequestSummary[]; onProcess: (requestId: string) => void }) {
  return <section className="panel" aria-labelledby="privacy-heading"><h2 id="privacy-heading">Privacy requests and SLA tracker</h2><div className="quote-table" role="table" aria-label="Privacy requests"><div role="row" className="quote-table__row quote-table__row--head"><span role="columnheader">Request ID</span><span role="columnheader">Borrower</span><span role="columnheader">Scope</span><span role="columnheader">Identity</span><span role="columnheader">SLA state</span><span role="columnheader">Consent audit</span><span role="columnheader">Blockers and action</span></div>{requests.length === 0 ? <div role="row" className="quote-table__row"><span role="cell">No privacy requests supplied.</span></div> : requests.map((request) => <div key={request.requestId} role="row" className="quote-table__row"><span role="cell"><code>{request.requestId}</code></span><span role="cell">{displayText(request.borrowerRef)}</span><span role="cell">{request.requestedScope}</span><span role="cell">{request.identityStatus}</span><span role="cell">{request.slaState}<br />SLA tracker uses configured service state only.</span><span role="cell"><code>{request.consentAuditRef}</code></span><span role="cell"><ExpandableRefList label={`${request.requestId} blockers`} values={request.blockers} emptyText="No privacy blockers." /><button type="button" onClick={() => onProcess(request.requestId)}>Process Request</button></span></div>)}</div></section>;
}

function SecurityPanel({ events }: { events: SecurityEventSummary[] }) {
  return <CardList title="Security events" items={events.map((event) => ({ id: event.eventId, title: `${event.category} - ${event.severity}`, body: [`Owner: ${displayText(event.owner)}`, `Log record ID: ${event.logRecordId}`, `Correlation ID: ${event.correlationId}`, `Acknowledged: ${event.acknowledged ? 'yes' : 'no'}`], refs: event.blockers, actions: <><button type="button" aria-label={`Acknowledge ${event.eventId}`}>Acknowledge</button><button type="button" aria-label={`Investigate ${event.eventId}`}>Investigate</button><button type="button" aria-label={`Resolve ${event.eventId}`}>Resolve</button><a href={`/security/events?event=${encodeURIComponent(event.eventId)}`}>View Log Record</a></> }))} emptyText="No security events supplied." />;
}

function AlertsPanel({ alerts, onNavigate }: { alerts: ComplianceAlertSummary[]; onNavigate?: (target: string) => void }) {
  return <CardList title="Compliance alerts" items={alerts.map((alert) => ({ id: alert.alertId, title: `${alert.severity} - ${displayText(alert.alertClass)}`, body: [`Trigger: ${displayText(alert.triggerType)}`, `Route target: ${displayText(alert.routeTarget)}`, `Acknowledged: ${alert.acknowledged ? 'yes' : 'no'}`], refs: alert.blockers, actions: <><button type="button" aria-label={`Acknowledge ${alert.alertId}`}>Acknowledge</button><button type="button" onClick={() => onNavigate?.(alert.routeTarget)}>Route to Owner</button></> }))} emptyText="No compliance alerts supplied." />;
}

function RetentionPanel({ controls, onLegalHold }: { controls: RetentionControlSummary[]; onLegalHold: (ruleId: string) => void }) {
  return <CardList title="Retention controls" items={controls.map((control) => ({ id: control.ruleId, title: control.ruleId, body: [`Retention class: ${control.retentionClass}`, `Retention window: ${displayText(control.retentionWindow)}`, `Legal hold active: ${control.legalHoldActive ? 'yes' : 'no'}`, `Deletion gate: ${control.deletionGateReason}`, `Backup evidence: ${control.backupEvidence}`], refs: [control.backupEvidence], actions: <><button type="button" onClick={() => onLegalHold(control.ruleId)}>Apply Legal Hold</button><button type="button" aria-label={`Trigger deletion for ${control.ruleId}`}>Trigger Deletion</button></> }))} emptyText="No retention controls supplied." />;
}

function ConfigGapsPanel({ gaps, onNavigate }: { gaps: string[]; onNavigate?: (target: string) => void }) {
  return <CardList title="Configuration gaps" items={gaps.map((gap) => ({ id: gap, title: displayText(gap), body: ['Affected module: compliance evidence registry', 'Severity: configured-service-ref-required', 'Remediation: governance owner review required', 'Owner: governance'], refs: [gap], actions: <button type="button" onClick={() => onNavigate?.('/admin/governance')}>View in Governance</button> }))} emptyText="No configuration gaps supplied." />;
}

function CardList({ title, items, emptyText }: { title: string; items: { id: string; title: string; body: string[]; refs: string[]; actions?: React.ReactNode }[]; emptyText: string }) {
  const headingId = `${title.toLowerCase().replace(/[^a-z0-9]+/g, '-')}-heading`;
  return <section className="panel" aria-labelledby={headingId}><h2 id={headingId}>{title}</h2>{items.length === 0 ? <div className="banner banner--info" role="status">{emptyText}</div> : <div className="partner-detail-grid" role="list" aria-label={title}>{items.map((item) => <article key={item.id} className="status-card status-card--offline" role="listitem"><h3>{item.title}</h3>{item.body.map((line) => <p key={line}>{line}</p>)}<ExpandableRefList label={`${item.id} refs`} values={item.refs} emptyText="No refs supplied." />{item.actions ? <div className="button-row">{item.actions}</div> : null}</article>)}</div>}</section>;
}

function ArtifactModal({ artifact, onClose }: { artifact: ComplianceEvidenceArtifact; onClose: () => void }) {
  return <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="artifact-modal-title"><div className="modal-card"><h2 id="artifact-modal-title">View Artifact {artifact.artifactId}</h2><dl className="status-grid"><dt>Path</dt><dd>{displayText(artifact.path)}</dd><dt>Hash verification</dt><dd><code>{artifact.hash}</code></dd><dt>Policy digest</dt><dd><code>{artifact.policyDigest}</code></dd><dt>Download</dt><dd><button type="button">Download metadata package</button></dd><dt>Redaction profile</dt><dd>Configured by compliance service</dd></dl><button type="button" onClick={onClose}>Close</button></div></div>;
}

function ProcessRequestModal({ request, onClose }: { request: PrivacyRequestSummary; onClose: () => void }) {
  return <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="privacy-modal-title"><div className="modal-card"><h2 id="privacy-modal-title">Process Request {request.requestId}</h2><p>Identity verification: {request.identityStatus}</p><p>Scope confirmation: {request.requestedScope}</p><p>Export remains blocked until configured privacy service permits it.</p><ExpandableRefList label={`${request.requestId} blockers`} values={request.blockers} emptyText="No blockers." /><button type="button" onClick={onClose}>Close</button></div></div>;
}

function LegalHoldModal({ control, onClose }: { control: RetentionControlSummary; onClose: () => void }) {
  return <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="legal-hold-modal-title"><div className="modal-card"><h2 id="legal-hold-modal-title">Apply Legal Hold {control.ruleId}</h2><p>Legal hold active: {control.legalHoldActive ? 'yes' : 'no'}</p><p>Deletion gate: {control.deletionGateReason}</p><p>Backup evidence: {control.backupEvidence}</p><button type="button" onClick={onClose}>Close</button></div></div>;
}

function ExpandableRefList({ label, values, emptyText }: { label: string; values: string[]; emptyText: string }) {
  return <details><summary>{label}</summary>{values.length === 0 ? <p>{emptyText}</p> : <ul>{values.map((value) => <li key={value}><code>{value}</code></li>)}</ul>}</details>;
}

function registryRefs(view: ComplianceEvidenceRegistryView) {
  return [...view.artifacts.flatMap((artifact) => [artifact.artifactId, artifact.hash, artifact.traceId, artifact.policyVersion, artifact.policyDigest, ...artifact.moduleLinks]), ...view.decisions.map((decision) => decision.disclosureArtifactRef), ...view.advisoryReviews.flatMap((review) => [...review.auditSnapshotRefs, ...review.exportRefs]), ...view.fairLendingMonitoring.flatMap((drilldown) => drilldown.evidenceRefs), ...view.privacyRequests.map((request) => request.consentAuditRef), ...view.securityEvents.flatMap((event) => [event.logRecordId, event.correlationId]), ...view.retentionControls.map((control) => control.backupEvidence)].filter(Boolean);
}

function uniqueValues(values: string[]) {
  return Array.from(new Set(values.filter(Boolean))).sort((left, right) => left.localeCompare(right));
}

function displayText(value: string) {
  return value.replace(/[_-]+/g, ' ').replace(/\b\w/g, (character) => character.toUpperCase());
}
