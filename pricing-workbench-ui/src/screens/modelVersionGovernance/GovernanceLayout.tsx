import { type FormEvent, useEffect, useState } from 'react';
import { DiagnosticsDetails } from '../../components/DiagnosticsDetails';
import { fetchModelVersionGovernance, type ModelVersionGovernanceView } from '../../lib/api/modelVersionGovernance';
import { ChipList, businessFacingText, serviceReadinessText } from '../mlAdvisoryInsights/shared';
import { blockedModelVersionGovernanceView } from './fixtures';

type GovernanceState =
  | { kind: 'loading' }
  | { kind: 'loaded'; view: ModelVersionGovernanceView }
  | { kind: 'unreachable'; message: string };

type GovernanceTab = 'registry' | 'approvals' | 'lifecycle' | 'compatibility' | 'monitoring';

const tabs: Array<{ id: GovernanceTab; label: string }> = [
  { id: 'registry', label: 'Registry' },
  { id: 'approvals', label: 'Approvals' },
  { id: 'lifecycle', label: 'Lifecycle' },
  { id: 'compatibility', label: 'Compatibility' },
  { id: 'monitoring', label: 'Monitoring' },
];

export function ModelVersionGovernanceScreen() {
  const [state, setState] = useState<GovernanceState>({ kind: 'loading' });
  const [activeTab, setActiveTab] = useState<GovernanceTab>('registry');
  const [registerOpen, setRegisterOpen] = useState(false);
  const [artifactModel, setArtifactModel] = useState<string | null>(null);
  const [formErrors, setFormErrors] = useState<string[]>([]);

  useEffect(() => {
    let active = true;
    fetchModelVersionGovernance()
      .then((view) => {
        if (active) setState({ kind: 'loaded', view });
      })
      .catch((error: unknown) => {
        if (active) setState({ kind: 'unreachable', message: error instanceof Error ? error.message : 'Model version governance is unavailable.' });
      });
    return () => {
      active = false;
    };
  }, []);

  function submitRegistration(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const required = ['modelId', 'version', 'artifactRef', 'metadataRef', 'validationRef'];
    const missing = required.filter((field) => !String(form.get(field) ?? '').trim());
    if (missing.length > 0) {
      setFormErrors(missing.map((field) => `${registrationFieldLabel(field)} is required.`));
      return;
    }
    setFormErrors(['Registration is validated locally, but submission remains disabled until the governance API provides an action contract.']);
  }

  if (state.kind === 'loading') {
    return <section className="panel" aria-labelledby="model-governance-loading-heading"><h2 id="model-governance-loading-heading">Model version governance loading state</h2><p role="status">Loading model governance data...</p></section>;
  }

  const view = state.kind === 'loaded' ? state.view : blockedModelVersionGovernanceView;

  return (
    <div className="ml-advisory-shell model-version-governance-shell">
      <section className="hero" aria-labelledby="model-governance-title">
        <p className="eyebrow">ML governance · PII-24-S26</p>
        <h2 id="model-governance-title">Model Version Governance</h2>
        <p>
          Manage model registry, approval, lifecycle, compatibility, monitoring, and audit evidence from connected-service values only.
          The browser does not infer approval criteria, drift thresholds, model policy, or mortgage pricing rules.
        </p>
      </section>

      <section className="panel" aria-labelledby="model-governance-context-heading">
        <div className="panel-heading-row">
          <div>
            <p className="eyebrow">Tenant context</p>
            <h2 id="model-governance-context-heading">Governance workspace</h2>
          </div>
          <DiagnosticsDetails items={[`Support reference: ${view.uiTraceId}`, `Workspace ${view.tenantContext}`]} />
        </div>
        <div className={state.kind === 'unreachable' ? 'banner banner--blocked' : 'banner banner--info'} role={state.kind === 'unreachable' ? 'alert' : 'status'}>
          <strong>{state.kind === 'unreachable' ? 'Governance API unavailable' : 'Governance data loaded'}</strong>
          <span>{state.kind === 'unreachable' ? state.message : businessFacingText(view.fallbackReason)}</span>
          <span>Setup status: {serviceReadinessText(view.dependencyStatus)}</span>
        </div>
      </section>

      <section className="panel" aria-labelledby="model-governance-tabs-heading">
        <div className="panel-heading-row sticky-header">
          <div>
            <p className="eyebrow">Lifecycle controls</p>
            <h2 id="model-governance-tabs-heading">Governance tabs</h2>
          </div>
          <ChipList label="Governance events" values={view.events.map(businessFacingText)} />
        </div>
        <div role="tablist" aria-label="Model version governance tabs" className="module-rail__grid">
          {tabs.map((tab) => (
            <button key={tab.id} type="button" role="tab" aria-selected={activeTab === tab.id} onClick={() => setActiveTab(tab.id)}>
              {tab.label}
            </button>
          ))}
        </div>

        {activeTab === 'registry' ? <RegistryTab view={view} onRegister={() => setRegisterOpen(true)} onArtifacts={setArtifactModel} /> : null}
        {activeTab === 'approvals' ? <ApprovalsTab view={view} /> : null}
        {activeTab === 'lifecycle' ? <LifecycleTab view={view} /> : null}
        {activeTab === 'compatibility' ? <CompatibilityTab view={view} /> : null}
        {activeTab === 'monitoring' ? <MonitoringTab view={view} /> : null}
      </section>

      {registerOpen ? <RegistrationDialog errors={formErrors} onClose={() => { setRegisterOpen(false); setFormErrors([]); }} onSubmit={submitRegistration} /> : null}
      {artifactModel ? <ArtifactsDialog view={view} modelKey={artifactModel} onClose={() => setArtifactModel(null)} /> : null}
    </div>
  );
}

function registrationFieldLabel(field: string) {
  const labels: Record<string, string> = {
    modelId: 'Model ID',
    version: 'Version',
    artifactRef: 'Artifact refs',
    metadataRef: 'Metadata',
    validationRef: 'Validation',
  };
  return labels[field] ?? businessFacingText(field);
}

function RegistryTab({ view, onRegister, onArtifacts }: { view: ModelVersionGovernanceView; onRegister: () => void; onArtifacts: (modelKey: string) => void }) {
  return (
    <div role="tabpanel" aria-label="Registry">
      <div className="panel-heading-row">
        <h3>Model registry</h3>
        <button type="button" onClick={onRegister}>Register Model</button>
      </div>
      <div className="quote-table" role="table" aria-label="Model registry">
        <div role="row" className="quote-table__row quote-table__row--head">
          <span role="columnheader">Model ID</span><span role="columnheader">Version</span><span role="columnheader">Status</span><span role="columnheader">Registered By</span><span role="columnheader">Registered At</span><span role="columnheader">Artifacts</span><span role="columnheader">Validation Status</span>
        </div>
        {view.registry.map((row) => (
          <div key={`${row.modelId}-${row.version}`} role="row" className="quote-table__row">
            <span role="cell"><strong>{row.modelId}</strong></span><span role="cell">{row.version}</span><span role="cell">{businessFacingText(row.status)}</span><span role="cell">{row.registeredBy}</span><span role="cell">{row.registeredAt}</span><span role="cell"><button type="button" onClick={() => onArtifacts(`${row.modelId}:${row.version}`)}>View Artifacts</button></span><span role="cell">{businessFacingText(row.validationStatus)}<ChipList label={`${row.modelId} validation messages`} values={row.validationMessages.map(businessFacingText)} /></span>
          </div>
        ))}
      </div>
    </div>
  );
}

function ApprovalsTab({ view }: { view: ModelVersionGovernanceView }) {
  return (
    <div role="tabpanel" aria-label="Approvals">
      <h3>Approval workflow</h3>
      <div className="quote-table" role="table" aria-label="Model approvals">
        <div role="row" className="quote-table__row quote-table__row--head">
          <span role="columnheader">Model ID</span><span role="columnheader">Version</span><span role="columnheader">Approval Status</span><span role="columnheader">Requested By</span><span role="columnheader">Approvers</span><span role="columnheader">Criteria</span><span role="columnheader">Decision</span>
        </div>
        {view.approvals.map((approval) => (
          <div key={`${approval.modelId}-${approval.version}`} role="row" className="quote-table__row">
            <span role="cell"><strong>{approval.modelId}</strong></span><span role="cell">{approval.version}</span><span role="cell">{businessFacingText(approval.approvalStatus)}</span><span role="cell">{approval.requestedBy}<br />{approval.requestedAt}</span><span role="cell"><ChipList label={`${approval.modelId} approvers`} values={approval.approvers} /></span><span role="cell"><CriteriaList criteria={approval.criteria} /></span><span role="cell">{businessFacingText(approval.decision)}<br /><button type="button" disabled={!view.actions.approvalDecisionEnabled}>Approve</button> <button type="button" disabled={!view.actions.approvalDecisionEnabled}>Reject</button><ChipList label={`${approval.modelId} approval audit events`} values={approval.auditEvents.map(businessFacingText)} /></span>
          </div>
        ))}
      </div>
      <p className="field-help">Approve and reject stay disabled until ML advisory supplies an authorized decision action contract.</p>
    </div>
  );
}

function CriteriaList({ criteria }: { criteria: ModelVersionGovernanceView['approvals'][number]['criteria'] }) {
  return <ul>{criteria.map((item) => <li key={item.criterionId}>{item.label}: {businessFacingText(item.status)} <code>{item.configuredValueRef ?? item.evidenceRef}</code></li>)}</ul>;
}

function LifecycleTab({ view }: { view: ModelVersionGovernanceView }) {
  return (
    <div role="tabpanel" aria-label="Lifecycle">
      <h3>Lifecycle transitions</h3>
      <div className="quote-table" role="table" aria-label="Model lifecycle">
        <div role="row" className="quote-table__row quote-table__row--head">
          <span role="columnheader">Model ID</span><span role="columnheader">Version</span><span role="columnheader">Current Status</span><span role="columnheader">Promotion</span><span role="columnheader">Suspension</span><span role="columnheader">Deprecation</span><span role="columnheader">Actions</span>
        </div>
        {view.lifecycle.map((row) => (
          <div key={`${row.modelId}-${row.version}`} role="row" className="quote-table__row">
            <span role="cell"><strong>{row.modelId}</strong></span><span role="cell">{row.version}</span><span role="cell">{businessFacingText(row.currentStatus)}</span><span role="cell">{row.promotedAt ?? 'Promotion pending'}<br />{row.promotedBy ?? row.monitoringConfigRef}</span><span role="cell">{row.suspendedAt ?? 'Not suspended'}<br />{row.suspendedBy ?? 'Reason required on suspend'}</span><span role="cell">{row.deprecatedAt ?? 'Not deprecated'}<br />{row.replacementModel}<br />{row.migrationTimeline}</span><span role="cell"><button type="button" disabled={!view.actions.lifecycleTransitionEnabled}>Promote</button> <button type="button" disabled={!view.actions.lifecycleTransitionEnabled}>Suspend</button> <button type="button" disabled={!view.actions.lifecycleTransitionEnabled}>Deprecate</button> <button type="button" disabled={!view.actions.lifecycleTransitionEnabled}>Reactivate</button><ChipList label={`${row.modelId} lifecycle blockers`} values={row.transitionBlockers.map(businessFacingText)} /></span>
          </div>
        ))}
      </div>
    </div>
  );
}

function CompatibilityTab({ view }: { view: ModelVersionGovernanceView }) {
  return (
    <div role="tabpanel" aria-label="Compatibility">
      <div className="panel-heading-row">
        <h3>Compatibility matrix</h3>
        <button type="button" disabled={!view.actions.compatibilityCheckEnabled}>Run Compatibility Check</button>
      </div>
      <div className="quote-table" role="table" aria-label="Model compatibility matrix">
        <div role="row" className="quote-table__row quote-table__row--head">
          <span role="columnheader">Model Version</span><span role="columnheader">Consumer Service</span><span role="columnheader">Status</span><span role="columnheader">Breaking Changes</span><span role="columnheader">Migration Path</span><span role="columnheader">Test Results</span>
        </div>
        {view.compatibility.map((row) => (
          <div key={`${row.modelVersion}-${row.consumerService}`} role="row" className="quote-table__row">
            <span role="cell"><strong>{row.modelVersion}</strong></span><span role="cell">{row.consumerService}</span><span role="cell">{businessFacingText(row.status)}</span><span role="cell"><ChipList label={`${row.consumerService} breaking changes`} values={row.breakingChanges.map(businessFacingText)} /></span><span role="cell">{row.migrationPath}</span><span role="cell"><ChipList label={`${row.consumerService} test results`} values={row.testResults} /></span>
          </div>
        ))}
      </div>
    </div>
  );
}

function MonitoringTab({ view }: { view: ModelVersionGovernanceView }) {
  return (
    <div role="tabpanel" aria-label="Monitoring">
      <h3>Drift Monitoring</h3>
      <MetricGrid label="Drift monitoring metrics" metrics={view.monitoring.drift} />
      <h3>Performance Monitoring</h3>
      <MetricGrid label="Performance monitoring metrics" metrics={view.monitoring.performance} />
      <h3>Alerting</h3>
      <div className="module-rail__grid" role="list" aria-label="Model monitoring alerts">
        {view.monitoring.alerting.map((alert) => (
          <article key={alert.alertId} className="module-card" role="listitem">
            <strong>{businessFacingText(alert.severity)}</strong>
            <p>{businessFacingText(alert.status)}</p>
            <p>Escalation: {alert.escalationOwner}</p>
            <ChipList label={`${alert.alertId} notification channels`} values={alert.notificationChannels} />
            {alert.blocker ? <p className="field-help">{alert.blocker}</p> : null}
          </article>
        ))}
      </div>
      <a href="/advisory/ml/drift">View Drift Details</a>
    </div>
  );
}

function MetricGrid({ label, metrics }: { label: string; metrics: ModelVersionGovernanceView['monitoring']['drift'] }) {
  return (
    <div className="module-rail__grid" role="list" aria-label={label}>
      {metrics.map((metric) => (
        <article key={metric.metricId} className="module-card" role="listitem">
          <strong>{metric.label}</strong>
          <p>{businessFacingText(metric.status)}</p>
          <dl><dt>Value</dt><dd>{metric.valueLabel}</dd><dt>Configured threshold</dt><dd><code>{metric.thresholdRef}</code></dd><dt>Evidence</dt><dd><code>{metric.evidenceRef}</code></dd></dl>
        </article>
      ))}
    </div>
  );
}

function RegistrationDialog({ errors, onClose, onSubmit }: { errors: string[]; onClose: () => void; onSubmit: (event: FormEvent<HTMLFormElement>) => void }) {
  return (
    <div role="dialog" aria-modal="true" aria-labelledby="register-model-heading" className="panel">
      <h3 id="register-model-heading">Register Model</h3>
      <form className="intake-form" onSubmit={onSubmit} noValidate>
        <label htmlFor="modelId">Model ID</label><input id="modelId" name="modelId" />
        <label htmlFor="version">Version</label><input id="version" name="version" />
        <label htmlFor="artifactRef">Artifact refs</label><textarea id="artifactRef" name="artifactRef" />
        <label htmlFor="metadataRef">Metadata</label><textarea id="metadataRef" name="metadataRef" />
        <label htmlFor="validationRef">Validation</label><textarea id="validationRef" name="validationRef" />
        {errors.length > 0 ? <div className="banner banner--blocked" role="alert"><ChipList label="Registration validation messages" values={errors} /></div> : null}
        <button type="submit">Validate registration</button>
        <button type="button" onClick={onClose}>Close</button>
      </form>
    </div>
  );
}

function ArtifactsDialog({ view, modelKey, onClose }: { view: ModelVersionGovernanceView; modelKey: string; onClose: () => void }) {
  const [modelId, version] = modelKey.split(':');
  const row = view.registry.find((item) => item.modelId === modelId && item.version === version);
  return (
    <div role="dialog" aria-modal="true" aria-labelledby="model-artifacts-heading" className="panel">
      <h3 id="model-artifacts-heading">View Artifacts</h3>
      {row ? <ul>{row.artifacts.map((artifact) => <li key={artifact.artifactId}><strong>{artifact.artifactId}</strong> · checksum <code>{artifact.checksum}</code> · location <code>{artifact.locationRef}</code></li>)}</ul> : <p>No artifact references supplied.</p>}
      <button type="button" onClick={onClose}>Close</button>
    </div>
  );
}
