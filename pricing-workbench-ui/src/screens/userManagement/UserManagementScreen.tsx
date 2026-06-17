import { useMemo, useState } from 'react';
import type { FormEvent } from 'react';
import type { UserRole } from '../../lib/api/auth';

type ManagementTab = 'users' | 'roles' | 'permissions' | 'tenant-access';
type UserStatus = 'ACTIVE' | 'INVITED' | 'SUSPENDED';

type ManagedUser = {
  id: string;
  name: string;
  email: string;
  roles: UserRole[];
  status: UserStatus;
  lastLogin: string;
};

type RoleDefinition = {
  name: UserRole;
  label: string;
  permissions: string[];
  scope: string;
};

type PermissionDefinition = {
  id: string;
  resource: string;
  actions: string[];
  roleMappings: UserRole[];
};

type TenantAccess = Record<string, string[]>;

const roleDefinitions: RoleDefinition[] = [
  { name: 'admin', label: 'Tenant administrator', permissions: ['tenant:read', 'tenant:update', 'users:manage', 'roles:assign'], scope: 'Tenant admin boundary' },
  { name: 'operations_lead', label: 'Operations lead', permissions: ['pipeline:read', 'cases:manage', 'locks:update'], scope: 'Operational work queues' },
  { name: 'pricing_analyst', label: 'Pricing analyst', permissions: ['products:read', 'pricing:analyze', 'ratesheets:manage'], scope: 'Pricing workbench' },
  { name: 'governance_reviewer', label: 'Governance reviewer', permissions: ['audit:read', 'policy:review', 'evidence:export'], scope: 'Governance review' },
  { name: 'partner_manager', label: 'Partner manager', permissions: ['partners:read', 'partner-feeds:manage'], scope: 'Partner integrations' },
  { name: 'compliance_officer', label: 'Compliance officer', permissions: ['compliance:read', 'fair-lending:review'], scope: 'Compliance evidence' },
  { name: 'loan_officer', label: 'Loan officer', permissions: ['pipeline:create', 'quotes:read', 'locks:request'], scope: 'Origination workflow' },
  { name: 'borrower', label: 'Borrower', permissions: ['portal:read'], scope: 'Borrower portal' },
];

const permissionDefinitions: PermissionDefinition[] = [
  { id: 'tenant-admin', resource: 'tenant-context-service.admin.tenants', actions: ['list', 'create', 'update', 'activate', 'suspend', 'feature-flags'], roleMappings: ['admin'] },
  { id: 'identity', resource: 'tenant-context-service.auth', actions: ['register', 'login', 'me', 'logout'], roleMappings: ['admin', 'loan_officer', 'pricing_analyst', 'operations_lead', 'governance_reviewer', 'partner_manager', 'compliance_officer', 'borrower'] },
  { id: 'pricing', resource: 'pricing-workbench.pricing', actions: ['read', 'analyze', 'export-evidence'], roleMappings: ['pricing_analyst', 'admin', 'loan_officer'] },
  { id: 'governance', resource: 'pricing-workbench.governance', actions: ['review', 'replay-audit', 'export'], roleMappings: ['governance_reviewer', 'compliance_officer', 'admin'] },
  { id: 'ops', resource: 'pricing-workbench.operations', actions: ['triage', 'bulk-update', 'lock-review'], roleMappings: ['operations_lead', 'admin'] },
];

const tenants = [
  { id: 'tenant-preview-001', name: 'Acme Mortgage Corp', status: 'PENDING_ACTIVATION', flags: ['quick_pricer', 'scenario_analysis'] },
  { id: 'tenant-preview-002', name: 'Regional Lending Preview', status: 'ACTIVE', flags: ['lock_management', 'mi_pricing', 'government_products'] },
  { id: 'tenant-preview-003', name: 'Broker Partner Sandbox', status: 'ACTIVE', flags: ['partner_integrations', 'quick_pricer'] },
];

const initialUsers: ManagedUser[] = [
  { id: 'user-001', name: 'Avery Brooks', email: 'avery.brooks@example.test', roles: ['admin', 'governance_reviewer'], status: 'ACTIVE', lastLogin: 'backend-ref:last-login-001' },
  { id: 'user-002', name: 'Priya Shah', email: 'priya.shah@example.test', roles: ['pricing_analyst'], status: 'ACTIVE', lastLogin: 'backend-ref:last-login-002' },
  { id: 'user-003', name: 'Marcus Lee', email: 'marcus.lee@example.test', roles: ['operations_lead'], status: 'INVITED', lastLogin: 'pending-first-login' },
  { id: 'user-004', name: 'Nora Kim', email: 'nora.kim@example.test', roles: ['loan_officer'], status: 'SUSPENDED', lastLogin: 'backend-ref:last-login-004' },
];

const initialAccess: TenantAccess = {
  'user-001': ['tenant-preview-001', 'tenant-preview-002'],
  'user-002': ['tenant-preview-001'],
  'user-003': ['tenant-preview-002', 'tenant-preview-003'],
  'user-004': ['tenant-preview-003'],
};

const tabLabels: Record<ManagementTab, string> = {
  users: 'Users',
  roles: 'Roles',
  permissions: 'Permissions',
  'tenant-access': 'Tenant Access',
};

export function UserManagementScreen() {
  const [activeTab, setActiveTab] = useState<ManagementTab>('users');
  const [category, setCategory] = useState('all');
  const [query, setQuery] = useState('');
  const [users, setUsers] = useState(initialUsers);
  const [tenantAccess, setTenantAccess] = useState<TenantAccess>(initialAccess);
  const [selectedUserIds, setSelectedUserIds] = useState<Set<string>>(new Set());
  const [addOpen, setAddOpen] = useState(false);
  const [ssoOpen, setSsoOpen] = useState(false);

  const roleCounts = useMemo(() => roleDefinitions.reduce<Record<UserRole, number>>((counts, role) => {
    counts[role.name] = users.filter((user) => user.roles.includes(role.name)).length;
    return counts;
  }, {} as Record<UserRole, number>), [users]);

  const filteredUsers = useMemo(() => users.filter((user) => {
    const matchesCategory = category === 'all'
      || user.status.toLowerCase() === category
      || user.roles.includes(category as UserRole);
    const normalized = query.trim().toLowerCase();
    const matchesQuery = !normalized
      || user.name.toLowerCase().includes(normalized)
      || user.email.toLowerCase().includes(normalized)
      || user.roles.some((role) => role.includes(normalized));
    return matchesCategory && matchesQuery;
  }), [category, query, users]);

  const selectedUsers = users.filter((user) => selectedUserIds.has(user.id));

  function toggleSelected(userId: string) {
    setSelectedUserIds((current) => {
      const next = new Set(current);
      if (next.has(userId)) next.delete(userId);
      else next.add(userId);
      return next;
    });
  }

  function bulkStatus(status: UserStatus) {
    setUsers((current) => current.map((user) => selectedUserIds.has(user.id) ? { ...user, status } : user));
  }

  function bulkAssignAdmin() {
    setUsers((current) => current.map((user) => selectedUserIds.has(user.id) && !user.roles.includes('admin') ? { ...user, roles: [...user.roles, 'admin'] } : user));
  }

  function createUser(payload: { name: string; email: string; role: UserRole; tenantIds: string[] }) {
    const id = `local-user-${Date.now()}`;
    setUsers((current) => [{ id, name: payload.name, email: payload.email, roles: [payload.role], status: 'INVITED', lastLogin: 'pending-first-login' }, ...current]);
    setTenantAccess((current) => ({ ...current, [id]: payload.tenantIds }));
    setAddOpen(false);
  }

  function toggleTenant(userId: string, tenantId: string) {
    setTenantAccess((current) => {
      const existing = current[userId] ?? [];
      const next = existing.includes(tenantId) ? existing.filter((id) => id !== tenantId) : [...existing, tenantId];
      return { ...current, [userId]: next };
    });
  }

  return (
    <section className="um-screen" aria-labelledby="user-management-title">
      <style>{screenStyles}</style>
      <header className="um-hero um-glass">
        <div>
          <p className="um-eyebrow">Tenant-context-service auth / TenantAdminController</p>
          <h1 id="user-management-title">User Management</h1>
          <p>Manage users, role assignments, permissions, and tenant access without embedding authorization decisions in the browser.</p>
        </div>
        <div className="um-hero__actions">
          <a className="um-button um-button--ghost" href="/audit/replay?scope=user-management">Audit log</a>
          <button className="um-button" type="button" onClick={() => setSsoOpen((open) => !open)}>SSO config</button>
          <button className="um-button um-button--primary" type="button" onClick={() => setAddOpen(true)}>Add user</button>
        </div>
      </header>

      <div className="um-layout">
        <aside className="um-sidebar um-glass" aria-label="User categories">
          <h2>User categories</h2>
          <CategoryButton label="All users" count={users.length} active={category === 'all'} onClick={() => setCategory('all')} />
          <CategoryButton label="Active" count={users.filter((user) => user.status === 'ACTIVE').length} active={category === 'active'} onClick={() => setCategory('active')} />
          <CategoryButton label="Invited" count={users.filter((user) => user.status === 'INVITED').length} active={category === 'invited'} onClick={() => setCategory('invited')} />
          <CategoryButton label="Suspended" count={users.filter((user) => user.status === 'SUSPENDED').length} active={category === 'suspended'} onClick={() => setCategory('suspended')} />
          <div className="um-sidebar__roles">
            {roleDefinitions.slice(0, 6).map((role) => <CategoryButton key={role.name} label={role.label} count={roleCounts[role.name] ?? 0} active={category === role.name} onClick={() => setCategory(role.name)} />)}
          </div>
          <div className="um-sidebar__card">
            <span>SSO readiness</span>
            <strong>Config required</strong>
            <small>Issuer, client id, redirects, scopes, and session ownership must come from approved environment configuration.</small>
          </div>
        </aside>

        <main className="um-main um-glass">
          <div className="um-tabs" role="tablist" aria-label="User management tabs">
            {(Object.keys(tabLabels) as ManagementTab[]).map((tab) => (
              <button key={tab} role="tab" aria-selected={activeTab === tab} className={activeTab === tab ? 'is-active' : ''} type="button" onClick={() => setActiveTab(tab)}>{tabLabels[tab]}</button>
            ))}
          </div>

          <div className="um-toolbar" role="search" aria-label="User management filters and bulk operations">
            <label>Search<input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Name, email, role" /></label>
            <span className="um-selected">{selectedUsers.length} selected</span>
            <button type="button" onClick={() => bulkStatus('ACTIVE')} disabled={!selectedUsers.length}>Bulk activate</button>
            <button type="button" onClick={() => bulkStatus('SUSPENDED')} disabled={!selectedUsers.length}>Bulk suspend</button>
            <button type="button" onClick={bulkAssignAdmin} disabled={!selectedUsers.length}>Assign admin role</button>
          </div>

          {ssoOpen ? <SsoPanel /> : null}
          {activeTab === 'users' ? <UsersGrid users={filteredUsers} tenantAccess={tenantAccess} selectedUserIds={selectedUserIds} onToggleSelected={toggleSelected} /> : null}
          {activeTab === 'roles' ? <RolesGrid roles={roleDefinitions} roleCounts={roleCounts} /> : null}
          {activeTab === 'permissions' ? <PermissionsGrid permissions={permissionDefinitions} /> : null}
          {activeTab === 'tenant-access' ? <TenantAccessGrid users={filteredUsers} tenantAccess={tenantAccess} onToggleTenant={toggleTenant} /> : null}
        </main>
      </div>

      {addOpen ? <AddUserPanel roles={roleDefinitions} tenants={tenants} onClose={() => setAddOpen(false)} onSubmit={createUser} /> : null}
    </section>
  );
}

function CategoryButton({ label, count, active, onClick }: { label: string; count: number; active: boolean; onClick: () => void }) {
  return <button className={`um-category ${active ? 'is-active' : ''}`} type="button" onClick={onClick}><span>{label}</span><strong>{count}</strong></button>;
}

function UsersGrid({ users, tenantAccess, selectedUserIds, onToggleSelected }: { users: ManagedUser[]; tenantAccess: TenantAccess; selectedUserIds: Set<string>; onToggleSelected: (userId: string) => void }) {
  return (
    <div className="um-grid" role="table" aria-label="Users grid">
      <div className="um-grid__row um-grid__row--head" role="row"><span role="columnheader">Select</span><span role="columnheader">Name</span><span role="columnheader">Email</span><span role="columnheader">Roles</span><span role="columnheader">Tenant access</span><span role="columnheader">Status</span><span role="columnheader">Last login</span></div>
      {users.map((user) => <div className="um-grid__row" role="row" key={user.id}>
        <span role="cell"><input aria-label={`Select ${user.name}`} type="checkbox" checked={selectedUserIds.has(user.id)} onChange={() => onToggleSelected(user.id)} /></span>
        <span role="cell"><strong>{user.name}</strong><small>{user.id}</small></span>
        <span role="cell">{user.email}</span>
        <span role="cell" className="um-chip-list">{user.roles.map((role) => <RoleChip key={role} role={role} />)}</span>
        <span role="cell" className="um-chip-list">{(tenantAccess[user.id] ?? []).map((tenantId) => <span className="um-chip" key={tenantId}>{tenantId}</span>)}</span>
        <span role="cell"><StatusPill status={user.status} /></span>
        <span role="cell"><code>{user.lastLogin}</code></span>
      </div>)}
    </div>
  );
}

function RolesGrid({ roles, roleCounts }: { roles: RoleDefinition[]; roleCounts: Record<UserRole, number> }) {
  return (
    <div className="um-grid um-grid--roles" role="table" aria-label="Roles grid">
      <div className="um-grid__row um-grid__row--head" role="row"><span role="columnheader">Name</span><span role="columnheader">Permissions</span><span role="columnheader">User count</span><span role="columnheader">Scope</span></div>
      {roles.map((role) => <div className="um-grid__row" role="row" key={role.name}>
        <span role="cell"><strong>{role.label}</strong><small>{role.name}</small></span>
        <span role="cell" className="um-chip-list">{role.permissions.map((permission) => <span className="um-chip" key={permission}>{permission}</span>)}</span>
        <span role="cell"><strong>{roleCounts[role.name] ?? 0}</strong></span>
        <span role="cell">{role.scope}</span>
      </div>)}
    </div>
  );
}

function PermissionsGrid({ permissions }: { permissions: PermissionDefinition[] }) {
  return (
    <div className="um-grid um-grid--permissions" role="table" aria-label="Permissions grid">
      <div className="um-grid__row um-grid__row--head" role="row"><span role="columnheader">Resource</span><span role="columnheader">Actions</span><span role="columnheader">Role mappings</span></div>
      {permissions.map((permission) => <div className="um-grid__row" role="row" key={permission.id}>
        <span role="cell"><strong>{permission.resource}</strong><small>{permission.id}</small></span>
        <span role="cell" className="um-chip-list">{permission.actions.map((action) => <span className="um-chip" key={action}>{action}</span>)}</span>
        <span role="cell" className="um-chip-list">{permission.roleMappings.map((role) => <RoleChip key={role} role={role} />)}</span>
      </div>)}
    </div>
  );
}

function TenantAccessGrid({ users, tenantAccess, onToggleTenant }: { users: ManagedUser[]; tenantAccess: TenantAccess; onToggleTenant: (userId: string, tenantId: string) => void }) {
  return (
    <div className="um-access" aria-label="Tenant access matrix">
      <div className="um-access__header um-glass"><span>User ↔ tenant matrix</span>{tenants.map((tenant) => <strong key={tenant.id}>{tenant.name}</strong>)}<span>Feature flags</span></div>
      {users.map((user) => <div className="um-access__row" key={user.id}>
        <strong>{user.name}<small>{user.email}</small></strong>
        {tenants.map((tenant) => {
          const checked = (tenantAccess[user.id] ?? []).includes(tenant.id);
          return <button key={tenant.id} type="button" className={checked ? 'is-granted' : ''} aria-pressed={checked} onClick={() => onToggleTenant(user.id, tenant.id)}>{checked ? 'Granted' : 'No access'}</button>;
        })}
        <span className="um-chip-list">{Array.from(new Set((tenantAccess[user.id] ?? []).flatMap((tenantId) => tenants.find((tenant) => tenant.id === tenantId)?.flags ?? []))).map((flag) => <span className="um-chip" key={flag}>{flag}</span>)}</span>
      </div>)}
    </div>
  );
}

function AddUserPanel({ roles, tenants: tenantOptions, onClose, onSubmit }: { roles: RoleDefinition[]; tenants: typeof tenants; onClose: () => void; onSubmit: (payload: { name: string; email: string; role: UserRole; tenantIds: string[] }) => void }) {
  const [selectedTenants, setSelectedTenants] = useState<string[]>(tenantOptions[0] ? [tenantOptions[0].id] : []);

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const name = String(data.get('name') ?? '').trim();
    const email = String(data.get('email') ?? '').trim();
    const role = String(data.get('role') ?? 'loan_officer') as UserRole;
    if (!name || !email) return;
    onSubmit({ name, email, role, tenantIds: selectedTenants });
  }

  function toggleTenant(tenantId: string) {
    setSelectedTenants((current) => current.includes(tenantId) ? current.filter((id) => id !== tenantId) : [...current, tenantId]);
  }

  return (
    <aside className="um-drawer um-glass" role="dialog" aria-modal="true" aria-labelledby="add-user-title">
      <form onSubmit={submit}>
        <header><p className="um-eyebrow">Role and tenant assignment</p><h2 id="add-user-title">Add user</h2><button type="button" onClick={onClose} aria-label="Close add user panel">×</button></header>
        <label>Full name<input name="name" required placeholder="Name from identity profile" /></label>
        <label>Email<input name="email" required type="email" placeholder="user@example.test" /></label>
        <label>Initial role<select name="role" defaultValue="loan_officer">{roles.map((role) => <option value={role.name} key={role.name}>{role.label}</option>)}</select></label>
        <fieldset><legend>Tenant access</legend>{tenantOptions.map((tenant) => <label className="um-check" key={tenant.id}><input type="checkbox" checked={selectedTenants.includes(tenant.id)} onChange={() => toggleTenant(tenant.id)} />{tenant.name}<small>{tenant.status}</small></label>)}</fieldset>
        <div className="um-drawer__footer"><button type="button" onClick={onClose}>Cancel</button><button className="um-button--primary" type="submit">Send invite</button></div>
      </form>
    </aside>
  );
}

function SsoPanel() {
  return <section className="um-sso um-glass" aria-label="SSO configuration"><strong>SSO configuration</strong><span>OIDC/PKCE setup remains configuration-owned. Required refs: issuer, client id, redirect URIs, scopes, token/session owner.</span><code>Auth endpoints: /api/auth/login, /api/auth/register, /api/auth/me, /api/auth/logout</code></section>;
}

function RoleChip({ role }: { role: UserRole }) {
  return <span className="um-chip um-chip--role">{role.replace(/_/g, ' ')}</span>;
}

function StatusPill({ status }: { status: UserStatus }) {
  return <span className={`um-status um-status--${status.toLowerCase()}`}>{status.replace('_', ' ')}</span>;
}

const screenStyles = `
.um-screen { color: #eaf2ff; display: grid; gap: 1rem; min-height: 100%; padding: clamp(1rem, 2vw, 1.5rem); width: 100%; }
.um-screen button, .um-screen input, .um-screen select { font: inherit; }
.um-glass { backdrop-filter: blur(22px); background: linear-gradient(135deg, rgb(255 255 255 / 15%), rgb(255 255 255 / 6%)); border: 1px solid rgb(255 255 255 / 18%); box-shadow: 0 24px 80px rgb(0 0 0 / 24%), inset 0 1px 0 rgb(255 255 255 / 12%); }
.um-hero { align-items: center; border-radius: 1.4rem; display: flex; gap: 1rem; justify-content: space-between; padding: 1.1rem; }
.um-eyebrow { color: #93c5fd; font-size: .72rem; font-weight: 900; letter-spacing: .12em; margin: 0 0 .25rem; text-transform: uppercase; }
.um-hero h1, .um-sidebar h2, .um-drawer h2 { margin: 0; }
.um-hero p { color: #cbd5e1; margin: .35rem 0 0; max-width: 58rem; }
.um-hero__actions, .um-toolbar, .um-chip-list { align-items: center; display: flex; flex-wrap: wrap; gap: .45rem; }
.um-button, .um-screen button, .um-button:visited { border: 1px solid rgb(255 255 255 / 18%); border-radius: .85rem; color: #eaf2ff; cursor: pointer; padding: .55rem .8rem; text-decoration: none; }
.um-screen button { background: rgb(15 23 42 / 58%); }
.um-screen button:disabled { cursor: not-allowed; opacity: .45; }
.um-button--primary, .um-screen .um-button--primary { background: linear-gradient(135deg, #38bdf8, #6366f1); color: #06111f; font-weight: 900; }
.um-button--ghost { background: rgb(15 23 42 / 40%); }
.um-layout { display: grid; gap: 1rem; grid-template-columns: minmax(13rem, 17rem) minmax(0, 1fr); width: 100%; }
.um-sidebar, .um-main { border-radius: 1.25rem; min-width: 0; padding: .8rem; }
.um-sidebar { align-content: start; display: grid; gap: .55rem; }
.um-sidebar__roles { border-top: 1px solid rgb(255 255 255 / 14%); display: grid; gap: .45rem; padding-top: .55rem; }
.um-sidebar__card { background: rgb(15 23 42 / 48%); border: 1px solid rgb(255 255 255 / 12%); border-radius: 1rem; display: grid; gap: .25rem; padding: .75rem; }
.um-sidebar__card small, .um-grid small, .um-access small { color: #94a3b8; display: block; }
.um-category { align-items: center; display: flex; justify-content: space-between; text-align: left; width: 100%; }
.um-category.is-active, .um-tabs button.is-active { background: linear-gradient(135deg, rgb(56 189 248 / 32%), rgb(99 102 241 / 36%)); border-color: rgb(147 197 253 / 56%); }
.um-main { display: grid; gap: .8rem; overflow: hidden; }
.um-tabs { display: flex; gap: .45rem; overflow-x: auto; }
.um-tabs button { white-space: nowrap; }
.um-toolbar { background: rgb(15 23 42 / 38%); border: 1px solid rgb(255 255 255 / 12%); border-radius: 1rem; padding: .6rem; }
.um-toolbar label { color: #bfdbfe; display: grid; font-size: .75rem; font-weight: 800; gap: .25rem; min-width: min(20rem, 100%); }
.um-screen input, .um-screen select { background: rgb(15 23 42 / 72%); border: 1px solid rgb(255 255 255 / 18%); border-radius: .75rem; color: #eaf2ff; min-height: 2.35rem; padding: .5rem .65rem; }
.um-selected { color: #bfdbfe; font-weight: 800; margin-inline: auto .3rem; }
.um-grid { border: 1px solid rgb(255 255 255 / 12%); border-radius: 1rem; display: grid; overflow: auto; }
.um-grid__row { align-items: center; display: grid; gap: .55rem; grid-template-columns: 4.2rem minmax(10rem, 1.1fr) minmax(12rem, 1.2fr) minmax(13rem, 1.2fr) minmax(13rem, 1.1fr) 7rem minmax(10rem, .9fr); min-width: 72rem; padding: .55rem .65rem; }
.um-grid--roles .um-grid__row { grid-template-columns: minmax(12rem, 1fr) minmax(22rem, 2fr) 7rem minmax(12rem, 1fr); min-width: 56rem; }
.um-grid--permissions .um-grid__row { grid-template-columns: minmax(18rem, 1.2fr) minmax(20rem, 1.4fr) minmax(18rem, 1.3fr); min-width: 58rem; }
.um-grid__row:nth-child(even), .um-access__row:nth-child(even) { background: rgb(15 23 42 / 32%); }
.um-grid__row--head { background: rgb(15 23 42 / 70%); color: #bfdbfe; font-size: .73rem; font-weight: 900; letter-spacing: .08em; position: sticky; text-transform: uppercase; top: 0; }
.um-chip { background: rgb(14 165 233 / 16%); border: 1px solid rgb(125 211 252 / 24%); border-radius: 999px; color: #dbeafe; display: inline-flex; font-size: .72rem; font-weight: 800; padding: .22rem .45rem; text-transform: capitalize; }
.um-chip--role { background: rgb(99 102 241 / 22%); }
.um-status { border-radius: 999px; display: inline-flex; font-size: .72rem; font-weight: 900; padding: .25rem .5rem; }
.um-status--active { background: rgb(34 197 94 / 18%); color: #bbf7d0; }
.um-status--invited { background: rgb(250 204 21 / 18%); color: #fef3c7; }
.um-status--suspended { background: rgb(248 113 113 / 18%); color: #fecaca; }
.um-access { display: grid; gap: .4rem; overflow: auto; }
.um-access__header, .um-access__row { align-items: center; border-radius: .9rem; display: grid; gap: .45rem; grid-template-columns: minmax(14rem, 1.2fr) repeat(3, minmax(10rem, 1fr)) minmax(16rem, 1.4fr); min-width: 68rem; padding: .55rem; }
.um-access__header { color: #bfdbfe; font-size: .76rem; font-weight: 900; text-transform: uppercase; }
.um-access__row { background: rgb(15 23 42 / 25%); border: 1px solid rgb(255 255 255 / 10%); }
.um-access__row button.is-granted { background: rgb(34 197 94 / 22%); border-color: rgb(134 239 172 / 40%); }
.um-sso { border-radius: 1rem; display: grid; gap: .35rem; padding: .75rem; }
.um-sso span { color: #cbd5e1; }
.um-drawer { bottom: 1rem; border-radius: 1.25rem; max-width: min(31rem, calc(100vw - 2rem)); overflow: auto; padding: 1rem; position: fixed; right: 1rem; top: 1rem; width: 31rem; z-index: 10; }
.um-drawer form, .um-drawer fieldset { display: grid; gap: .75rem; }
.um-drawer header { align-items: start; display: flex; justify-content: space-between; }
.um-drawer label { display: grid; gap: .25rem; }
.um-drawer fieldset { border: 1px solid rgb(255 255 255 / 16%); border-radius: 1rem; }
.um-check { align-items: center; display: grid; gap: .2rem .5rem; grid-template-columns: auto 1fr; }
.um-check small { color: #94a3b8; grid-column: 2; }
.um-drawer__footer { display: flex; gap: .5rem; justify-content: flex-end; }
@media (max-width: 860px) { .um-hero { align-items: start; flex-direction: column; } .um-layout { grid-template-columns: 1fr; } .um-sidebar { position: static; } .um-selected { margin-inline: 0; } }
`;

export default UserManagementScreen;
