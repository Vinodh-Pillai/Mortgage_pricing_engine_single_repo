import {
  canAccessRoute,
  getPersonaByRole,
  hasPermission as personaHasPermission,
  roleFromVisibility,
  type Permission,
  type Persona,
} from '../../lib/auth/personas';

export type HomeRole =
  | 'loan_officer'
  | 'pricing_analyst'
  | 'ops'
  | 'operations_lead'
  | 'borrower'
  | 'admin'
  | 'compliance'
  | 'governance'
  | 'partner_manager';

export type QuickAction = {
  id: string;
  label: string;
  route: string;
  icon: string;
  requiredPermissions: Permission[];
  permissionMatch?: 'all' | 'any';
  unavailableReason?: string;
  auditHelpText?: string;
};

export const quickActions: QuickAction[] = [
  { id: 'new-pipeline', label: 'New Pipeline', route: '/pipeline', icon: '＋', requiredPermissions: ['quote:create', 'scenario:create'] },
  {
    id: 'rate-sheet-upload',
    label: 'Rate Sheet Review',
    route: '/pricing/rate-sheets',
    icon: '⇧',
    requiredPermissions: ['rate-sheet:read', 'rate-feed:read'],
    permissionMatch: 'any',
    unavailableReason: 'Configured tenant/product mappings are unavailable until catalog and rate-feed metadata are connected.',
  },
  { id: 'quote-workspace', label: 'Quote Workspace', route: '/quote/current/offers', icon: '◷', requiredPermissions: ['quote:read'] },
  { id: 'lock-management', label: 'Lock Management', route: '/locks', icon: '🔒', requiredPermissions: ['lock:read'] },
  { id: 'pricing-analysis', label: 'Pricing Analysis', route: '/pricing/analysis', icon: 'ƒ', requiredPermissions: ['pricing:analysis', 'pricing:read'], permissionMatch: 'any' },
  {
    id: 'product-catalog',
    label: 'Product Catalog Review',
    route: '/admin/products/catalog',
    icon: '▦',
    requiredPermissions: ['product:read'],
    unavailableReason: 'Product mappings are visible as setup blockers until configured catalog metadata is available.',
    auditHelpText: 'Product catalog changes require audit evidence before setup changes are promoted.',
  },
  { id: 'ops-remediation', label: 'Operational Remediation', route: '/ops/dashboard', icon: '⟲', requiredPermissions: ['ops:manage', 'rate-feed:manage'], permissionMatch: 'any' },
  { id: 'tenant-settings', label: 'Tenant Settings', route: '/tenant/onboarding', icon: '⚙', requiredPermissions: ['admin:*'], auditHelpText: 'Tenant management changes must be traceable in audit history before activation.' },
  { id: 'user-management', label: 'User Access', route: '/admin/users', icon: '☻', requiredPermissions: ['admin:*'], auditHelpText: 'User access updates require audit evidence and least-privilege review.' },
  { id: 'compliance-evidence', label: 'Compliance Evidence', route: '/compliance/evidence', icon: '✓', requiredPermissions: ['compliance:read', 'governance:read'], permissionMatch: 'any' },
];

type QuickActionsProps = {
  role: HomeRole | string;
  onNavigate: (route: string) => void;
};

export function QuickActions({ role, onNavigate }: QuickActionsProps) {
  const persona = personaForRole(role);
  const visibleActions = persona ? quickActions.filter((action) => hasPermission(role, action)) : [];
  const showAuditHelp = normalizeRole(role) === 'admin';

  return (
    <section className="home-card" aria-labelledby="quick-actions-heading">
      <div className="home-section-heading">
        <p className="eyebrow">Quick actions</p>
        <h2 id="quick-actions-heading">Start work</h2>
      </div>
      {!persona ? (
        <p role="status" className="home-muted">Role metadata unavailable. Restricted actions are hidden until the session scope can be refreshed.</p>
      ) : null}
      <div className="quick-action-grid">
        {visibleActions.map((action) => {
          const auditHelpId = showAuditHelp && action.auditHelpText ? `${action.id}-audit` : '';
          const describedBy = [action.unavailableReason ? `${action.id}-reason` : '', auditHelpId].filter(Boolean).join(' ') || undefined;
          return (
            <button className="quick-action-card" key={action.id} type="button" onClick={() => onNavigate(action.route)} aria-describedby={describedBy}>
              <span className="quick-action-icon" aria-hidden="true">{action.icon}</span>
              <span>{action.label}</span>
              {showAuditHelp && action.auditHelpText ? <span className="quick-action-help-chip" aria-hidden="true" title={action.auditHelpText}>?</span> : null}
              {action.unavailableReason ? <small id={`${action.id}-reason`}>{action.unavailableReason}</small> : null}
              {showAuditHelp && action.auditHelpText ? <small id={`${action.id}-audit`} className="ds-visually-hidden">{action.auditHelpText}</small> : null}
            </button>
          );
        })}
      </div>
    </section>
  );
}

export function hasPermission(role: HomeRole | string, action: QuickAction) {
  const persona = personaForRole(role);
  if (!persona) return false;
  const permissionAllowed = action.permissionMatch === 'any'
    ? action.requiredPermissions.some((permission) => personaHasPermission(persona, permission))
    : action.requiredPermissions.every((permission) => personaHasPermission(persona, permission));
  return permissionAllowed && canAccessRoute(persona, action.route);
}

export function normalizeRole(role: HomeRole | string) {
  const normalized = role.trim().toLowerCase().replace(/[\s-]+/g, '_');
  if (normalized === 'operations_lead') return 'ops';
  if (normalized === 'governance_reviewer') return 'governance';
  return normalized;
}

export function personaForRole(role: HomeRole | string): Persona | null {
  const roleValue = String(role).trim();
  if (!roleValue) return null;
  const directRole = roleFromVisibility(roleValue);
  if (directRole) return getPersonaByRole(directRole) ?? null;
  const normalized = normalizeRole(roleValue);
  const aliasRole = roleFromVisibility(normalized === 'ops' ? 'operations-lead' : normalized.replace(/_/g, '-'));
  return aliasRole ? getPersonaByRole(aliasRole) ?? null : null;
}
