import { useEffect, useMemo, useState } from 'react';
import {
  createTenantAdminRecord,
  fetchTenantAdminList,
  fetchTenantFeatureFlags,
  updateTenantStatus,
  type TenantAdminRecord,
  type TenantCreatePayload,
  type TenantFeatureFlagsResponse,
  type TenantStatus,
} from '../../lib/api/tenants';
import type { ScreenEvidence, ScreenVisualState } from '../contract/ScreenProps';

type TenantAdminScreenProps = {
  fetchImpl?: typeof fetch;
  evidence?: TenantAdminRecord[];
  onEvidenceCapture?: (evidence: ScreenEvidence) => void;
};

type TenantAdminState =
  | { kind: 'loading' }
  | { kind: 'loaded'; tenants: TenantAdminRecord[]; source: 'api' | 'local-preview'; message: string }
  | { kind: 'blocked'; tenants: TenantAdminRecord[]; message: string };

type TenantFormState = TenantCreatePayload;

const evidenceTarget = '.local-harness/evidence/PII-52-S01/tenant-admin.json';
const emptyForm: TenantFormState = {
  tenantName: '',
  displayName: '',
  contactEmail: '',
  contactPhone: '',
  addressLine1: '',
  city: '',
  state: '',
  postalCode: '',
  country: 'US',
  nmlsId: '',
  logoUrl: '',
  primaryColor: '#1E40AF',
  secondaryColor: '#3B82F6',
};

const localPreviewTenants: TenantAdminRecord[] = [
  {
    tenantId: 'tenant-preview-001',
    name: 'acme-mortgage',
    displayName: 'Acme Mortgage Corp',
    status: 'PENDING_ACTIVATION',
    createdAt: 'backend-ref:tenant-preview-created-at',
    assignedUserCount: 0,
    logoUrl: 'backend-ref:logo-url',
    primaryColor: '#1E40AF',
    secondaryColor: '#3B82F6',
    contactEmail: 'backend-ref:tenant-contact-email',
    city: 'Springfield',
    state: 'IL',
    postalCode: '62701',
    country: 'US',
    nmlsId: '123456',
  },
  {
    tenantId: 'tenant-preview-002',
    name: 'regional-lending',
    displayName: 'Regional Lending Preview',
    status: 'ACTIVE',
    createdAt: 'backend-ref:tenant-active-created-at',
    activatedAt: 'backend-ref:tenant-activated-at',
    assignedUserCount: 42,
    primaryColor: '#0F766E',
    secondaryColor: '#14B8A6',
    country: 'US',
  },
];

const fallbackFlags: TenantFeatureFlagsResponse = {
  tenantId: 'tenant-preview-001',
  flags: {
    non_qm_pricing: { enabled: false },
    heloc_pricing: { enabled: false },
    reverse_mortgage: { enabled: false },
    government_products: { enabled: false },
    mi_pricing: { enabled: false },
    quick_pricer: { enabled: false },
    lock_management: { enabled: false },
    scenario_analysis: { enabled: false },
    partner_integrations: { enabled: false },
    ml_advisory: { enabled: false },
  },
};

const featureCategories: Record<string, string> = {
  non_qm_pricing: 'Non-QM',
  heloc_pricing: 'Core Pricing',
  reverse_mortgage: 'Advanced',
  government_products: 'Government',
  mi_pricing: 'Core Pricing',
  quick_pricer: 'Core Pricing',
  lock_management: 'Core Pricing',
  scenario_analysis: 'Advanced',
  partner_integrations: 'Advanced',
  ml_advisory: 'Advanced',
};

export function TenantAdminScreen({ fetchImpl, evidence, onEvidenceCapture }: TenantAdminScreenProps) {
  const [state, setState] = useState<TenantAdminState>(() => evidence ? { kind: 'loaded', tenants: evidence, source: 'local-preview', message: 'Injected tenant admin evidence.' } : { kind: 'loading' });
  const [query, setQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState<'all' | TenantStatus>('all');
  const [showTenantModal, setShowTenantModal] = useState(false);
  const [editingTenant, setEditingTenant] = useState<TenantAdminRecord | null>(null);
  const [form, setForm] = useState<TenantFormState>(emptyForm);
  const [featureTenant, setFeatureTenant] = useState<TenantAdminRecord | null>(null);
  const [featureFlags, setFeatureFlags] = useState<TenantFeatureFlagsResponse>(fallbackFlags);

  useEffect(() => {
    if (evidence) return;
    let active = true;
    fetchTenantAdminList(fetchImpl)
      .then((response) => {
        if (active) setState({ kind: 'loaded', tenants: response.content, source: 'api', message: 'Tenant admin API connected.' });
      })
      .catch((error: unknown) => {
        const message = error instanceof Error ? error.message : 'Tenant admin API is unavailable.';
        if (active) setState({ kind: 'blocked', tenants: localPreviewTenants, message: `${message} Showing local preview records and disabled backend write affordances.` });
      });
    return () => { active = false; };
  }, [evidence, fetchImpl]);

  const tenants = state.kind === 'loading' ? [] : state.tenants;
  const filteredTenants = useMemo(() => tenants.filter((tenant) => {
    const search = query.trim().toLowerCase();
    const searchMatches = !search || tenant.name.toLowerCase().includes(search) || tenant.displayName.toLowerCase().includes(search);
    const statusMatches = statusFilter === 'all' || tenant.status === statusFilter;
    return searchMatches && statusMatches;
  }).sort((a, b) => a.displayName.localeCompare(b.displayName)), [query, statusFilter, tenants]);

  useEffect(() => {
    if (state.kind === 'loading') return;
    onEvidenceCapture?.({
      screenId: 'tenant-admin',
      timestamp: new Date().toISOString(),
      state: tenantAdminVisualState(state.kind, tenants),
      dataRefs: collectEvidenceRefs(tenants),
      blockers: state.kind === 'blocked' ? [state.message] : [],
    });
  }, [onEvidenceCapture, state.kind, tenants]);

  if (state.kind === 'loading') {
    return <section className="panel" aria-labelledby="tenant-admin-heading"><h2 id="tenant-admin-heading">Tenant Management</h2><p role="status">Loading tenant administration...</p></section>;
  }

  const openCreate = () => {
    setEditingTenant(null);
    setForm(emptyForm);
    setShowTenantModal(true);
  };

  const openEdit = (tenant: TenantAdminRecord) => {
    setEditingTenant(tenant);
    setForm({
      tenantName: tenant.name,
      displayName: tenant.displayName,
      contactEmail: tenant.contactEmail ?? '',
      contactPhone: tenant.contactPhone ?? '',
      addressLine1: tenant.addressLine1 ?? '',
      city: tenant.city ?? '',
      state: tenant.state ?? '',
      postalCode: tenant.postalCode ?? '',
      country: tenant.country ?? 'US',
      nmlsId: tenant.nmlsId ?? '',
      logoUrl: tenant.logoUrl ?? '',
      primaryColor: tenant.primaryColor ?? '#1E40AF',
      secondaryColor: tenant.secondaryColor ?? '#3B82F6',
    });
    setShowTenantModal(true);
  };

  const saveTenant = async () => {
    if (!form.tenantName.trim() || !form.displayName.trim()) return;
    if (editingTenant) {
      setState({ kind: 'blocked', tenants, message: 'Tenant profile updates require tenant-context-service admin persistence before local rows can change.' });
      setShowTenantModal(false);
      return;
    }
    try {
      const created = await createTenantAdminRecord(form, fetchImpl);
      setState({ kind: 'loaded', source: 'api', message: 'Tenant created through admin API.', tenants: [created, ...tenants] });
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : 'Tenant creation requires tenant-context-service admin persistence.';
      setState({ kind: 'blocked', tenants, message });
    }
    setShowTenantModal(false);
  };

  const changeStatus = async (tenant: TenantAdminRecord, action: 'activate' | 'suspend' | 'deactivate') => {
    try {
      const updated = await updateTenantStatus(tenant.tenantId, action, fetchImpl);
      setState({ ...state, tenants: tenants.map((item) => item.tenantId === tenant.tenantId ? updated : item) });
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : `Tenant ${action} requires tenant-context-service admin persistence.`;
      setState({ kind: 'blocked', tenants, message });
    }
  };

  const openFeatureFlags = async (tenant: TenantAdminRecord) => {
    setFeatureTenant(tenant);
    try {
      setFeatureFlags(await fetchTenantFeatureFlags(tenant.tenantId, fetchImpl));
    } catch {
      setFeatureFlags({ ...fallbackFlags, tenantId: tenant.tenantId });
    }
  };

  return (
    <>
      <section className="hero hero--admin" aria-labelledby="tenant-admin-title">
        <p className="eyebrow">Tenant creation and management - PII-52-S01</p>
        <h2 id="tenant-admin-title">Tenant Management</h2>
        <p>Manage tenant lifecycle status, branding references, user counts, and tenant feature flags without embedding pricing or authorization rules in the browser.</p>
      </section>

      <section className="panel" aria-labelledby="tenant-admin-heading">
        <div className="panel-heading-row">
          <div><p className="eyebrow">Admin workspace</p><h2 id="tenant-admin-heading">Tenant lifecycle table</h2></div>
          <button type="button" onClick={openCreate}>Create Tenant</button>
        </div>
        <div className={state.kind === 'blocked' ? 'banner banner--blocked' : 'banner banner--info'} role={state.kind === 'blocked' ? 'alert' : 'status'}>{state.message}</div>
        <dl className="status-grid"><dt>Evidence target</dt><dd><code>{evidenceTarget}</code></dd><dt>Tenant rows</dt><dd>{tenants.length}</dd><dt>Feature categories</dt><dd>Core Pricing, Non-QM, Government, Advanced</dd></dl>
        <div className="intake-form" role="search" aria-label="Tenant search and filters">
          <label>Search by tenant name<input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search tenants" /></label>
          <label>Status<select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value as 'all' | TenantStatus)}><option value="all">All statuses</option><option value="PENDING_ACTIVATION">Pending activation</option><option value="ACTIVE">Active</option><option value="SUSPENDED">Suspended</option><option value="DEACTIVATED">Deactivated</option></select></label>
        </div>
        <TenantTable tenants={filteredTenants} onEdit={openEdit} onStatus={changeStatus} onFeatures={openFeatureFlags} />
      </section>

      {showTenantModal ? <TenantModal form={form} editing={Boolean(editingTenant)} onChange={setForm} onClose={() => setShowTenantModal(false)} onSave={saveTenant} /> : null}
      {featureTenant ? <FeatureFlagsModal tenant={featureTenant} flags={featureFlags} onClose={() => setFeatureTenant(null)} /> : null}
    </>
  );
}

function TenantTable({ tenants, onEdit, onStatus, onFeatures }: { tenants: TenantAdminRecord[]; onEdit: (tenant: TenantAdminRecord) => void; onStatus: (tenant: TenantAdminRecord, action: 'activate' | 'suspend' | 'deactivate') => void; onFeatures: (tenant: TenantAdminRecord) => void }) {
  return (
    <div className="quote-table" role="table" aria-label="Tenant management table">
      <div role="row" className="quote-table__row quote-table__row--head"><span role="columnheader">Name</span><span role="columnheader">Display Name</span><span role="columnheader">Status</span><span role="columnheader">Users</span><span role="columnheader">Features</span><span role="columnheader">Created</span><span role="columnheader">Actions</span></div>
      {tenants.length === 0 ? <div role="row" className="quote-table__row"><span role="cell">No tenants match the current filters.</span></div> : tenants.map((tenant) => (
        <div key={tenant.tenantId} role="row" className="quote-table__row">
          <span role="cell"><strong>{tenant.name}</strong><br /><code>{tenant.tenantId}</code></span>
          <span role="cell">{tenant.displayName}<br /><small>{tenant.contactEmail ?? 'contact-ref:backend-owned'}</small></span>
          <span role="cell"><span className={`functionality-badge functionality-badge--${statusClass(tenant.status)}`}>{statusLabel(tenant.status)}</span></span>
          <span role="cell"><strong>{tenant.assignedUserCount}</strong></span>
          <span role="cell"><button type="button" onClick={() => onFeatures(tenant)}>Feature Flags</button></span>
          <span role="cell"><code>{tenant.createdAt}</code></span>
          <span role="cell" className="button-row"><button type="button" onClick={() => onEdit(tenant)}>Edit</button><button type="button" disabled={tenant.status === 'ACTIVE'} onClick={() => onStatus(tenant, 'activate')}>Activate</button><button type="button" disabled={tenant.status !== 'ACTIVE'} onClick={() => onStatus(tenant, 'suspend')}>Suspend</button><button type="button" disabled={tenant.status === 'DEACTIVATED'} onClick={() => onStatus(tenant, 'deactivate')}>Deactivate</button><button type="button" disabled title="User assignment details require identity-service integration.">View Users</button></span>
        </div>
      ))}
    </div>
  );
}

function TenantModal({ form, editing, onChange, onClose, onSave }: { form: TenantFormState; editing: boolean; onChange: (form: TenantFormState) => void; onClose: () => void; onSave: () => void }) {
  return (
    <section className="panel panel--nested" role="dialog" aria-modal="true" aria-labelledby="tenant-modal-heading">
      <div className="panel-heading-row"><div><p className="eyebrow">{editing ? 'Edit tenant' : 'Create tenant'}</p><h2 id="tenant-modal-heading">Tenant profile and branding</h2></div><button type="button" onClick={onClose}>Close</button></div>
      <div className="intake-form">
        <label>Tenant Name<input value={form.tenantName} disabled={editing} onChange={(event) => onChange({ ...form, tenantName: event.target.value })} required /></label>
        <label>Display Name<input value={form.displayName} onChange={(event) => onChange({ ...form, displayName: event.target.value })} required /></label>
        <label>Contact Email<input value={form.contactEmail} onChange={(event) => onChange({ ...form, contactEmail: event.target.value })} /></label>
        <label>Phone<input value={form.contactPhone} onChange={(event) => onChange({ ...form, contactPhone: event.target.value })} /></label>
        <label>Address Line 1<input value={form.addressLine1} onChange={(event) => onChange({ ...form, addressLine1: event.target.value })} /></label>
        <label>City<input value={form.city} onChange={(event) => onChange({ ...form, city: event.target.value })} /></label>
        <label>State<input value={form.state} onChange={(event) => onChange({ ...form, state: event.target.value })} /></label>
        <label>ZIP<input value={form.postalCode} onChange={(event) => onChange({ ...form, postalCode: event.target.value })} /></label>
        <label>Country<input value={form.country} onChange={(event) => onChange({ ...form, country: event.target.value })} /></label>
        <label>NMLS ID<input value={form.nmlsId} onChange={(event) => onChange({ ...form, nmlsId: event.target.value })} /></label>
        <label>Logo URL<input value={form.logoUrl} onChange={(event) => onChange({ ...form, logoUrl: event.target.value })} /></label>
        <label>Primary Color<input type="color" value={form.primaryColor} onChange={(event) => onChange({ ...form, primaryColor: event.target.value })} /></label>
        <label>Secondary Color<input type="color" value={form.secondaryColor} onChange={(event) => onChange({ ...form, secondaryColor: event.target.value })} /></label>
      </div>
      <p className="field-help">Unique-name validation is enforced by tenant-context-service; this local modal only checks required fields before submit.</p>
      <button type="button" onClick={onSave} disabled={!form.tenantName.trim() || !form.displayName.trim()}>{editing ? 'Save Tenant' : 'Create Tenant'}</button>
    </section>
  );
}

function FeatureFlagsModal({ tenant, flags, onClose }: { tenant: TenantAdminRecord; flags: TenantFeatureFlagsResponse; onClose: () => void }) {
  const entries = Object.entries(flags.flags);
  return (
    <section className="panel panel--nested" role="dialog" aria-modal="true" aria-labelledby="feature-flags-heading">
      <div className="panel-heading-row"><div><p className="eyebrow">{tenant.displayName}</p><h2 id="feature-flags-heading">Feature Flags</h2></div><button type="button" onClick={onClose}>Close</button></div>
      <div className="module-rail__grid" role="list" aria-label="Tenant feature flag grid">
        {entries.map(([featureKey, flag]) => (
          <article key={featureKey} className="module-card module-card--light" role="listitem">
            <p className="module-card__route">{featureCategories[featureKey] ?? 'Configured Feature'}</p>
            <strong className="module-card__title">{featureKey.replace(/_/g, ' ')}</strong>
            <label><input type="checkbox" checked={flag.enabled} readOnly /> Enabled</label>
            <details><summary>Config JSON</summary><textarea aria-label={`${featureKey} config JSON`} readOnly value={JSON.stringify(flag.config ?? {}, null, 2)} /></details>
          </article>
        ))}
      </div>
      <p className="field-help">Saving feature flag changes requires the backend PATCH contract; this local view exposes the flags and config affordance.</p>
    </section>
  );
}

function statusLabel(status: TenantStatus) {
  return status.replace(/_/g, ' ');
}

function statusClass(status: TenantStatus) {
  if (status === 'ACTIVE') return 'ready';
  if (status === 'SUSPENDED') return 'needs-attention';
  if (status === 'DEACTIVATED') return 'blocked';
  return 'empty';
}

function collectEvidenceRefs(tenants: TenantAdminRecord[]) {
  return [evidenceTarget, ...tenants.flatMap((tenant) => [tenant.tenantId, tenant.createdAt, tenant.activatedAt, tenant.suspendedAt, tenant.deactivatedAt].filter(Boolean) as string[])];
}

function tenantAdminVisualState(kind: TenantAdminState['kind'], tenants: TenantAdminRecord[]): ScreenVisualState {
  if (kind === 'blocked') return 'blocked';
  if (kind === 'loading') return 'loading';
  return tenants.length === 0 ? 'empty' : 'ready';
}

export default TenantAdminScreen;
