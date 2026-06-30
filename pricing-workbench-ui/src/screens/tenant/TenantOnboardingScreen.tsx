import { useEffect, useMemo, useState } from 'react';
import {
  createTenantWorkspace,
  fetchTenantAdminList,
  updateTenantStatus,
  type TenantAdminRecord,
  type TenantSetupRequest,
  type TenantSetupResult,
  type TenantStatus,
} from '../../lib/api/tenants';
import type { EvidenceCapture } from '../shared/MajorFunctionalityPage';
import type { ScreenVisualState } from '../contract/ScreenProps';

type TenantOnboardingProps = {
  visualState?: ScreenVisualState;
  fetchImpl?: typeof fetch;
  onEvidenceCapture?: EvidenceCapture;
};

type TenantOnboardingState =
  | { kind: 'loading'; tenants: TenantAdminRecord[]; message: string }
  | { kind: 'loaded'; tenants: TenantAdminRecord[]; message: string }
  | { kind: 'blocked'; tenants: TenantAdminRecord[]; message: string };

const tenantOnboardingEvidenceTarget = '.local-harness/evidence/PII-25-S04/tenant-onboarding.json';
const emptySetup: TenantSetupRequest = { tenantName: '', operationsContact: '', launchGoal: '' };

const localPreviewTenants: TenantAdminRecord[] = [
  {
    tenantId: 'tenant-preview-onboarding',
    name: 'acme-mortgage',
    displayName: 'Acme Mortgage Corp',
    status: 'PENDING_ACTIVATION',
    createdAt: 'backend-ref:tenant-preview-created-at',
    assignedUserCount: 0,
    contactEmail: 'backend-ref:tenant-contact-email',
    city: 'Springfield',
    state: 'IL',
    postalCode: '62701',
    country: 'US',
  },
];

export function TenantOnboardingScreen({ visualState, fetchImpl, onEvidenceCapture }: TenantOnboardingProps) {
  const [state, setState] = useState<TenantOnboardingState>(() => visualState
    ? stateFromVisualOverride(visualState)
    : { kind: 'loading', tenants: [], message: 'Loading tenant onboarding records...' });
  const [setup, setSetup] = useState<TenantSetupRequest>(emptySetup);
  const [setupResult, setSetupResult] = useState<TenantSetupResult | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (visualState) {
      setState(stateFromVisualOverride(visualState));
      return undefined;
    }
    let active = true;
    setState({ kind: 'loading', tenants: [], message: 'Loading tenant onboarding records...' });
    fetchTenantAdminList(fetchImpl)
      .then((response) => {
        if (active) setState({ kind: 'loaded', tenants: response.content, message: 'Tenant admin API connected for onboarding and change review.' });
      })
      .catch((error: unknown) => {
        const message = error instanceof Error ? error.message : 'Tenant admin API is unavailable.';
        if (active) setState({ kind: 'blocked', tenants: localPreviewTenants, message: `${message} Showing local preview onboarding records.` });
      });
    return () => { active = false; };
  }, [fetchImpl, visualState]);

  const tenants = state.tenants;
  const refs = useMemo(() => [tenantOnboardingEvidenceTarget, ...tenants.map((tenant) => tenant.tenantId), ...(setupResult?.tenantId ? [setupResult.tenantId] : [])], [setupResult?.tenantId, tenants]);
  const missingRequiredSetup = !setup.tenantName.trim() || !setup.operationsContact.trim() || !setup.launchGoal.trim();

  useEffect(() => {
    onEvidenceCapture?.({
      screenId: 'tenant-onboarding',
      timestamp: new Date().toISOString(),
      state: tenantOnboardingVisualState(state),
      dataRefs: refs,
      blockers: state.kind === 'blocked' ? [state.message] : [],
      evidenceTarget: tenantOnboardingEvidenceTarget,
      refs,
    });
  }, [onEvidenceCapture, refs, state]);

  async function submitSetup() {
    if (missingRequiredSetup) return;
    setSubmitting(true);
    try {
      const result = await createTenantWorkspace(setup, fetchImpl);
      setSetupResult(result);
      if (result.tenantId) {
        const tenant: TenantAdminRecord = {
          tenantId: result.tenantId,
          name: normalizedTenantName(setup.tenantName),
          displayName: setup.tenantName,
          status: 'PENDING_ACTIVATION',
          createdAt: 'local-preview:tenant-workspace-created',
          assignedUserCount: 0,
          contactEmail: setup.operationsContact,
        };
        setState((current) => ({ ...current, kind: current.kind === 'loading' ? 'loaded' : current.kind, tenants: [tenant, ...current.tenants], message: result.message }));
      }
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : 'Tenant setup is temporarily unavailable.';
      setSetupResult({ tenantId: null, status: 'BLOCKED', message, nextStep: 'Retry after tenant-service setup is available.', placeholders: [message] });
      setState((current) => ({ ...current, kind: 'blocked', tenants: current.tenants.length ? current.tenants : localPreviewTenants, message }));
    } finally {
      setSubmitting(false);
    }
  }

  async function changeTenantStatus(tenant: TenantAdminRecord, action: 'activate' | 'suspend' | 'deactivate') {
    const nextStatus: TenantStatus = action === 'activate' ? 'ACTIVE' : action === 'suspend' ? 'SUSPENDED' : 'DEACTIVATED';
    try {
      const updated = await updateTenantStatus(tenant.tenantId, action, fetchImpl);
      setState((current) => ({ ...current, tenants: current.tenants.map((item) => item.tenantId === tenant.tenantId ? updated : item), message: `${tenant.displayName} ${action} request completed.` }));
    } catch {
      setState((current) => ({ ...current, tenants: current.tenants.map((item) => item.tenantId === tenant.tenantId ? { ...item, status: nextStatus } : item), message: `${tenant.displayName} ${action} captured locally until tenant-context-service accepts writes.` }));
    }
  }

  if (state.kind === 'loading') {
    return <section className="panel" aria-labelledby="tenant-onboarding-title"><h2 id="tenant-onboarding-title">Tenant Onboarding</h2><p role="status">{state.message}</p></section>;
  }

  return (
    <>
      <section className="hero hero--admin" aria-labelledby="tenant-onboarding-title">
        <p className="eyebrow">LoanWeft employee workspace</p>
        <h2 id="tenant-onboarding-title">Tenant Onboarding</h2>
        <p>Create a tenant workspace, review identity and launch readiness, and manage tenant lifecycle changes from tenant-owned APIs.</p>
      </section>

      <section className="panel" aria-labelledby="workspace-setup-heading">
        <div className="panel-heading-row">
          <div><p className="eyebrow">Step 1</p><h2 id="workspace-setup-heading">Workspace Setup</h2></div>
          <span className={`functionality-badge functionality-badge--${state.kind === 'blocked' ? 'blocked' : 'ready'}`}>{state.kind === 'blocked' ? 'setup attention' : 'api connected'}</span>
        </div>
        <div className={state.kind === 'blocked' ? 'banner banner--blocked' : 'banner banner--info'} role={state.kind === 'blocked' ? 'alert' : 'status'}>{state.message}</div>
        <div className="intake-form" aria-label="Tenant onboarding form">
          <label>Tenant name<input value={setup.tenantName} onChange={(event) => setSetup({ ...setup, tenantName: event.target.value })} required /></label>
          <label>Operations contact<input value={setup.operationsContact} onChange={(event) => setSetup({ ...setup, operationsContact: event.target.value })} required /></label>
          <label>Launch goal<textarea value={setup.launchGoal} onChange={(event) => setSetup({ ...setup, launchGoal: event.target.value })} required /></label>
        </div>
        <button type="button" className="quote-intake-primary" onClick={() => void submitSetup()} disabled={missingRequiredSetup || submitting}>{submitting ? 'Creating tenant...' : 'Create tenant workspace'}</button>
        {setupResult ? <div className={setupResult.status === 'BLOCKED' ? 'banner banner--blocked' : 'banner banner--info'} role={setupResult.status === 'BLOCKED' ? 'alert' : 'status'}>{setupResult.message} {setupResult.nextStep}</div> : null}
      </section>

      <section className="section-grid" aria-label="Tenant onboarding readiness sections">
        <article className="section-card"><p className="eyebrow">Step 2</p><h2>Identity Configuration</h2><p>Role maps, employee authorization, and identity provider references stay tenant-context-service owned.</p></article>
        <article className="section-card"><p className="eyebrow">Step 3</p><h2>Launch Checklist</h2><p>Activation remains gated by tenant status, assigned users, feature flags, and configured integration evidence.</p></article>
      </section>

      <section className="panel" aria-labelledby="tenant-change-heading">
        <div className="panel-heading-row"><div><p className="eyebrow">Tenant change module</p><h2 id="tenant-change-heading">Tenant lifecycle changes</h2></div><code>{tenantOnboardingEvidenceTarget}</code></div>
        <div className="quote-table" role="table" aria-label="Tenant onboarding and change table">
          <div role="row" className="quote-table__row quote-table__row--head"><span role="columnheader">Tenant</span><span role="columnheader">Status</span><span role="columnheader">Contact</span><span role="columnheader">Users</span><span role="columnheader">Actions</span></div>
          {tenants.length === 0 ? <div role="row" className="quote-table__row"><span role="cell">No tenant onboarding records are available.</span></div> : tenants.map((tenant) => (
            <div key={tenant.tenantId} role="row" className="quote-table__row">
              <span role="cell"><strong>{tenant.displayName}</strong><br /><code>{tenant.tenantId}</code></span>
              <span role="cell"><span className={`functionality-badge functionality-badge--${statusClass(tenant.status)}`}>{tenant.status.replace(/_/g, ' ')}</span></span>
              <span role="cell">{tenant.contactEmail ?? 'tenant-contact-ref-required'}</span>
              <span role="cell">{tenant.assignedUserCount}</span>
              <span role="cell" className="button-row"><button type="button" disabled={tenant.status === 'ACTIVE'} onClick={() => void changeTenantStatus(tenant, 'activate')}>Activate</button><button type="button" disabled={tenant.status !== 'ACTIVE'} onClick={() => void changeTenantStatus(tenant, 'suspend')}>Suspend</button><button type="button" disabled={tenant.status === 'DEACTIVATED'} onClick={() => void changeTenantStatus(tenant, 'deactivate')}>Deactivate</button></span>
            </div>
          ))}
        </div>
      </section>
    </>
  );
}

function stateFromVisualOverride(visualState: ScreenVisualState): TenantOnboardingState {
  if (visualState === 'loading') return { kind: 'loading', tenants: [], message: 'Loading tenant onboarding records...' };
  if (visualState === 'empty') return { kind: 'loaded', tenants: [], message: 'No tenant onboarding records are available.' };
  if (visualState === 'blocked' || visualState === 'needs-attention') return { kind: 'blocked', tenants: localPreviewTenants, message: 'Tenant onboarding needs configured tenant-context-service support.' };
  return { kind: 'loaded', tenants: localPreviewTenants, message: 'Tenant onboarding records loaded.' };
}

function tenantOnboardingVisualState(state: TenantOnboardingState): ScreenVisualState {
  if (state.kind === 'loading') return 'loading';
  if (state.kind === 'blocked') return 'blocked';
  return state.tenants.length === 0 ? 'empty' : 'ready';
}

function normalizedTenantName(value: string) {
  return value.trim().toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '') || 'tenant';
}

function statusClass(status: TenantStatus) {
  if (status === 'ACTIVE') return 'ready';
  if (status === 'SUSPENDED') return 'needs-attention';
  if (status === 'DEACTIVATED') return 'blocked';
  return 'empty';
}

export { tenantOnboardingEvidenceTarget };
export default TenantOnboardingScreen;
