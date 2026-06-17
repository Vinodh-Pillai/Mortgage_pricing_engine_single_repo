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
  roles: HomeRole[];
};

export const quickActions: QuickAction[] = [
  { id: 'new-pipeline', label: 'New Pipeline', route: '/pipeline', icon: '＋', roles: ['loan_officer', 'borrower', 'admin'] },
  { id: 'rate-sheet-upload', label: 'Rate Sheet Upload', route: '/pricing/rate-sheets/new', icon: '⇧', roles: ['pricing_analyst', 'ops', 'admin'] },
  { id: 'lock-management', label: 'Lock Management', route: '/locks', icon: '🔒', roles: ['ops', 'loan_officer', 'admin'] },
  { id: 'pricing-analysis', label: 'Pricing Analysis', route: '/pricing/analysis', icon: 'ƒ', roles: ['pricing_analyst', 'admin'] },
  { id: 'product-catalog', label: 'Product Catalog', route: '/admin/products/catalog', icon: '▦', roles: ['admin', 'pricing_analyst'] },
  { id: 'tenant-settings', label: 'Tenant Settings', route: '/tenant/onboarding', icon: '⚙', roles: ['admin', 'ops'] },
  { id: 'compliance-evidence', label: 'Compliance Evidence', route: '/compliance/evidence', icon: '✓', roles: ['compliance', 'governance', 'admin'] },
];

type QuickActionsProps = {
  role: HomeRole | string;
  onNavigate: (route: string) => void;
};

export function QuickActions({ role, onNavigate }: QuickActionsProps) {
  const visibleActions = quickActions.filter((action) => hasPermission(role, action));

  return (
    <section className="home-card" aria-labelledby="quick-actions-heading">
      <div className="home-section-heading">
        <p className="eyebrow">Quick actions</p>
        <h2 id="quick-actions-heading">Start work</h2>
      </div>
      <div className="quick-action-grid">
        {visibleActions.map((action) => (
          <button className="quick-action-card" key={action.id} type="button" onClick={() => onNavigate(action.route)}>
            <span className="quick-action-icon" aria-hidden="true">{action.icon}</span>
            <span>{action.label}</span>
          </button>
        ))}
      </div>
    </section>
  );
}

export function hasPermission(role: HomeRole | string, action: QuickAction) {
  const normalized = normalizeRole(role);
  return action.roles.includes(normalized as HomeRole);
}

export function normalizeRole(role: HomeRole | string) {
  const normalized = role.trim().toLowerCase().replace(/[\s-]+/g, '_');
  if (normalized === 'operations_lead') return 'ops';
  if (normalized === 'governance_reviewer') return 'governance';
  return normalized;
}
