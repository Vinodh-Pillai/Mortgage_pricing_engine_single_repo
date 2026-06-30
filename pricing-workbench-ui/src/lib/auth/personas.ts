import type { WorkbenchScreenModule } from '../../screens/workbenchShell/WorkbenchShell';

export type Permission = `${string}:${string}` | '*' | `${string}:*`;

export type PersonaRole =
  | 'loan-officer'
  | 'pricing-analyst'
  | 'operations-lead'
  | 'governance-reviewer'
  | 'admin'
  | 'partner-manager'
  | 'compliance-officer'
  | 'borrower';

export interface Persona {
  id: string;
  name: string;
  role: PersonaRole;
  email: string;
  avatar?: string;
  description: string;
  permissions: Permission[];
  defaultRoute: string;
  syntheticFixtureMetadata?: {
    classification: 'synthetic-test-only';
    workflowCoverage: string[];
    missingProductionIntegrations: string[];
  };
}

export type RoutePermissionRule = {
  pattern: string;
  permissions: Permission[];
  match: 'all' | 'any';
};

export const ACTIVE_PERSONA_STORAGE_KEY = 'wcpe:activePersona';

export const roleLabels: Record<PersonaRole, string> = {
  'loan-officer': 'Loan officer',
  'pricing-analyst': 'Pricing analyst',
  'operations-lead': 'Operations lead',
  'governance-reviewer': 'Governance reviewer',
  admin: 'Admin',
  'partner-manager': 'Partner manager',
  'compliance-officer': 'Compliance officer',
  borrower: 'Borrower',
};

export const syntheticPersonas: Persona[] = [
  {
    id: 'persona-loan-officer',
    name: 'Sarah Mitchell',
    role: 'loan-officer',
    email: 'sarah.mitchell@loanweft.demo',
    avatar: 'SM',
    description: 'Loan officer focused on borrower intake, quote creation, lock workflow, eligibility review, and pipeline follow-up.',
    permissions: [
      'quote:create',
      'quote:read',
      'quote:update',
      'scenario:create',
      'scenario:read',
      'scenario:update',
      'lock:create',
      'lock:read',
      'lock:update',
      'eligibility:read',
      'borrower:manage',
    ],
    defaultRoute: '/quote/start',
    syntheticFixtureMetadata: {
      classification: 'synthetic-test-only',
      workflowCoverage: [
        'pipeline-intake',
        'quick-quote',
        'quote-comparison',
        'request-lock',
        'lock-status',
        'lock-expiry',
        'lock-extension',
        'scenario-analysis',
        'what-if',
      ],
      missingProductionIntegrations: [
        'Live LOS/LoanPASS borrower import remains mocked for local E2E fixtures.',
        'Investor lock submission, expiry, and extension integrations remain fixture-backed.',
        'Pricing/rate values used by Sarah workflows are deterministic synthetic fixtures, not production pricing rules.',
      ],
    },
  },
  {
    id: 'persona-pricing-analyst',
    name: 'David Chen',
    role: 'pricing-analyst',
    email: 'david.chen@loanweft.demo',
    avatar: 'DC',
    description: 'Pricing analyst focused on pricing waterfall review, margin analysis, adjustment evidence, product review, and what-if scenarios.',
    permissions: [
      'quote:read',
      'pricing:read',
      'pricing:waterfall',
      'margin:read',
      'margin:analyze',
      'scenario:read',
      'scenario:what-if',
      'eligibility:read',
      'adjustment:read',
      'rate-feed:read',
      'product:read',
      'pricing:analysis',
      'tenant:onboard',
    ],
    defaultRoute: '/pricing/margins',
  },
  {
    id: 'persona-operations-lead',
    name: 'Maria Rodriguez',
    role: 'operations-lead',
    email: 'maria.rodriguez@loanweft.demo',
    avatar: 'MR',
    description: 'Operations lead managing lock exceptions, operations queues, rate-feed readiness, case triage, and partner operational follow-up.',
    permissions: [
      'quote:read',
      'lock:create',
      'lock:read',
      'lock:update',
      'lock:manage',
      'partner:read',
      'partner:manage',
      'ops:read',
      'ops:manage',
      'rate-feed:read',
      'rate-feed:manage',
      'rate-sheet:read',
      'rate-sheet:manage',
      'tenant:manage',
      'tenant:onboard',
    ],
    defaultRoute: '/ops/dashboard',
  },
  {
    id: 'persona-governance-reviewer',
    name: 'James Thompson',
    role: 'governance-reviewer',
    email: 'james.thompson@loanweft.demo',
    avatar: 'JT',
    description: 'Governance reviewer focused on compliance evidence, audit replay, rule governance, model governance, and approval readiness.',
    permissions: [
      'quote:read',
      'compliance:read',
      'compliance:manage',
      'audit:read',
      'audit:replay',
      'governance:read',
      'governance:manage',
      'rules:read',
      'rules:manage',
      'model:read',
      'model:governance',
      'quality:read',
      'tenant:onboard',
    ],
    defaultRoute: '/admin/governance',
  },
  {
    id: 'persona-admin',
    name: 'Admin User',
    role: 'admin',
    email: 'admin@loanweft.demo',
    avatar: 'AD',
    description: 'System administrator with unrestricted synthetic access to all local workbench modules.',
    permissions: ['*'],
    defaultRoute: '/admin/governance',
  },
  {
    id: 'persona-partner-manager',
    name: 'Lisa Park',
    role: 'partner-manager',
    email: 'lisa.park@loanweft.demo',
    avatar: 'LP',
    description: 'Partner manager handling partner quotes, integration health, webhook delivery, partner safety, and partner communication.',
    permissions: [
      'partner:read',
      'partner:manage',
      'partner:quotes',
      'partner:integrations',
      'webhook:manage',
      'quote:read',
      'lock:read',
    ],
    defaultRoute: '/partners/quotes',
  },
  {
    id: 'persona-compliance-officer',
    name: 'Robert Kim',
    role: 'compliance-officer',
    email: 'robert.kim@loanweft.demo',
    avatar: 'RK',
    description: 'Compliance officer focused on compliance evidence, audit records, privacy events, and security review surfaces.',
    permissions: [
      'compliance:read',
      'compliance:manage',
      'audit:read',
      'audit:replay',
      'privacy:read',
      'privacy:manage',
      'security:read',
      'security:events',
    ],
    defaultRoute: '/compliance/evidence',
  },
  {
    id: 'persona-borrower',
    name: 'Alex Johnson',
    role: 'borrower',
    email: 'alex.johnson@borrower.demo',
    avatar: 'AJ',
    description: 'Borrower persona for consumer-direct quick quote, eligibility visibility, and offer comparison.',
    permissions: [
      'quote:create',
      'quote:read',
      'offer:compare',
      'scenario:create',
      'eligibility:read',
    ],
    defaultRoute: '/quote/start',
  },
];

export const roleHierarchy: Record<PersonaRole, PersonaRole[]> = {
  admin: ['admin', 'loan-officer', 'pricing-analyst', 'operations-lead', 'governance-reviewer', 'partner-manager', 'compliance-officer', 'borrower'],
  'loan-officer': ['loan-officer', 'borrower'],
  'pricing-analyst': ['pricing-analyst'],
  'operations-lead': ['operations-lead'],
  'governance-reviewer': ['governance-reviewer'],
  'partner-manager': ['partner-manager'],
  'compliance-officer': ['compliance-officer'],
  borrower: ['borrower'],
};

export const routePermissionRules: RoutePermissionRule[] = [
  { pattern: '/login', permissions: [], match: 'all' },
  { pattern: '/', permissions: ['quote:create'], match: 'all' },
  { pattern: '/home', permissions: ['quote:read'], match: 'all' },
  { pattern: '/pipeline*', permissions: ['quote:create'], match: 'all' },
  { pattern: '/quote/start', permissions: ['quote:create', 'scenario:create'], match: 'all' },
  { pattern: '/quote/*/offers/*', permissions: ['quote:read'], match: 'all' },
  { pattern: '/quote/*/offers', permissions: ['quote:read', 'offer:compare'], match: 'any' },
  { pattern: '/quote/*/pricing-waterfall', permissions: ['pricing:read', 'quote:read'], match: 'any' },
  { pattern: '/quote/*/journey', permissions: ['quote:read'], match: 'all' },
  { pattern: '/quote/*/what-if*', permissions: ['scenario:what-if', 'scenario:read'], match: 'any' },
  { pattern: '/quote/*/lock', permissions: ['lock:read'], match: 'all' },
  { pattern: '/quote/*/status', permissions: ['lock:read'], match: 'all' },
  { pattern: '/quote/*/eligibility', permissions: ['eligibility:read'], match: 'all' },
  { pattern: '/quote/*', permissions: ['quote:read'], match: 'all' },
  { pattern: '/pricing/margins', permissions: ['margin:read'], match: 'all' },
  { pattern: '/pricing/waterfall', permissions: ['pricing:read', 'quote:read'], match: 'any' },
  { pattern: '/pricing/adjustments', permissions: ['pricing:read'], match: 'all' },
  { pattern: '/pricing/profiles*', permissions: ['pricing:read', 'admin:*'], match: 'any' },
  { pattern: '/pricing/rate-sheets*', permissions: ['rate-sheet:read', 'rate-feed:read'], match: 'any' },
  { pattern: '/pricing/analysis*', permissions: ['pricing:analysis', 'pricing:read', 'quote:read'], match: 'any' },
  { pattern: '/admin/products', permissions: ['product:read', 'admin:*'], match: 'any' },
  { pattern: '/admin/products/*', permissions: ['product:read', 'admin:*'], match: 'any' },
  { pattern: '/admin/ratefeed/*', permissions: ['rate-feed:read', 'rate-sheet:read', 'admin:*'], match: 'any' },
  { pattern: '/lock-management', permissions: ['lock:read'], match: 'all' },
  { pattern: '/locks*', permissions: ['lock:read'], match: 'all' },
  { pattern: '/journey-map', permissions: ['quote:read'], match: 'all' },
  { pattern: '/exceptions/concessions', permissions: ['margin:read', 'governance:read'], match: 'any' },
  { pattern: '/partners/*', permissions: ['partner:read'], match: 'all' },
  { pattern: '/ops/dashboard', permissions: ['ops:read'], match: 'all' },
  { pattern: '/ops/queues*', permissions: ['ops:read'], match: 'all' },
  { pattern: '/ops/cases*', permissions: ['ops:read'], match: 'all' },
  { pattern: '/ops/escalations*', permissions: ['ops:read'], match: 'all' },
  { pattern: '/ops/rate-feeds', permissions: ['rate-feed:read'], match: 'all' },
  { pattern: '/ops/performance', permissions: ['ops:read'], match: 'all' },
  { pattern: '/compliance/evidence', permissions: ['compliance:read'], match: 'all' },
  { pattern: '/privacy/requests*', permissions: ['privacy:read'], match: 'all' },
  { pattern: '/security/events*', permissions: ['audit:read', 'privacy:read'], match: 'any' },
  { pattern: '/quality/validation', permissions: ['quality:read', 'governance:read'], match: 'any' },
  { pattern: '/tenant/onboarding', permissions: ['tenant:onboard', 'tenant:manage', 'admin:*'], match: 'any' },
  { pattern: '/admin/tenants/new', permissions: ['tenant:onboard', 'tenant:manage', 'admin:*'], match: 'any' },
  { pattern: '/admin/*', permissions: ['admin:*', 'governance:read'], match: 'any' },
  { pattern: '/rules-engine*', permissions: ['rules:read', 'admin:*', 'governance:read'], match: 'any' },
  { pattern: '/custom-rules*', permissions: ['rules:read'], match: 'all' },
  { pattern: '/platform/tenant-context', permissions: ['*'], match: 'all' },
  { pattern: '/audit/replay', permissions: ['audit:read'], match: 'all' },
  { pattern: '/advisory/ml/governance', permissions: ['governance:read'], match: 'all' },
  { pattern: '/advisory/ml/drift', permissions: ['governance:read'], match: 'all' },
  { pattern: '/advisory/ml', permissions: ['governance:read'], match: 'all' },
  { pattern: '/service-modules', permissions: ['admin:*', 'governance:read'], match: 'any' },
];

const roleAliasEntries: Array<[PersonaRole, string[]]> = [
  ['loan-officer', ['loan-officer', 'loan officer', 'loan_officer', 'lo']],
  ['pricing-analyst', ['pricing-analyst', 'pricing analyst', 'pricing_analyst']],
  ['operations-lead', ['operations-lead', 'operations lead', 'operations_lead', 'ops lead']],
  ['governance-reviewer', ['governance-reviewer', 'governance reviewer', 'governance_reviewer']],
  ['admin', ['admin', 'administrator']],
  ['partner-manager', ['partner-manager', 'partner manager', 'partner_manager']],
  ['compliance-officer', ['compliance-officer', 'compliance officer', 'compliance_officer']],
  ['borrower', ['borrower', 'consumer']],
];

function normalizePath(path: string): string {
  const [withoutHash] = path.split('#');
  const [pathname] = withoutHash.split('?');
  if (!pathname) return '/';
  return pathname.startsWith('/') ? pathname : `/${pathname}`;
}

function patternToRegex(pattern: string): RegExp {
  const escaped = pattern
    .replace(/[.+?^${}()|[\]\\]/g, '\\$&')
    .replace(/\*/g, '.*');
  return new RegExp(`^${escaped}\/?$`);
}

function normalizeRole(value: string): string {
  return value.trim().toLowerCase().replace(/_/g, '-');
}

export function roleFromVisibility(value: string): PersonaRole | undefined {
  const normalized = normalizeRole(value);
  return roleAliasEntries.find(([, aliases]) => aliases.some((alias) => normalizeRole(alias) === normalized))?.[0];
}

export function getPersonaById(id: string): Persona | undefined {
  return syntheticPersonas.find((persona) => persona.id === id || persona.role === id);
}

export function getPersonasByRole(role: PersonaRole): Persona[] {
  return syntheticPersonas.filter((persona) => persona.role === role);
}

export function getPersonaByRole(role: PersonaRole): Persona | undefined {
  return syntheticPersonas.find((persona) => persona.role === role);
}

export function getEffectiveRoles(role: PersonaRole): PersonaRole[] {
  return roleHierarchy[role] ?? [role];
}

export function getEffectivePermissions(persona: Persona): Permission[] {
  if (persona.permissions.includes('*')) return ['*'];
  const permissionSet = new Set<Permission>();
  for (const role of getEffectiveRoles(persona.role)) {
    const rolePersona = getPersonaByRole(role);
    rolePersona?.permissions.forEach((permission) => permissionSet.add(permission));
  }
  persona.permissions.forEach((permission) => permissionSet.add(permission));
  return Array.from(permissionSet);
}

export function hasPermission(persona: Persona, permission: Permission): boolean {
  const permissions = getEffectivePermissions(persona);
  if (permissions.includes('*')) return true;
  if (permissions.includes(permission)) return true;
  const [domain] = permission.split(':');
  return permissions.includes(`${domain}:*` as Permission) || permissions.includes('admin:*');
}

export function matchesRoutePattern(route: string, pattern: string): boolean {
  return patternToRegex(pattern).test(normalizePath(route));
}

export function permissionsForRoute(route: string): RoutePermissionRule | undefined {
  const path = normalizePath(route);
  return routePermissionRules.find((rule) => matchesRoutePattern(path, rule.pattern));
}

export function canAccessRoute(persona: Persona, route: string): boolean {
  if (persona.permissions.includes('*')) return true;
  const rule = permissionsForRoute(route);
  if (!rule) return false;
  if (rule.permissions.length === 0) return true;
  return rule.match === 'all'
    ? rule.permissions.every((permission) => hasPermission(persona, permission))
    : rule.permissions.some((permission) => hasPermission(persona, permission));
}

export function canAssumeRole(persona: Persona, visibleRole: PersonaRole): boolean {
  return getEffectiveRoles(persona.role).includes(visibleRole);
}

export function canSeePersonaVisibility(persona: Persona, personaVisibility?: string[]): boolean {
  if (persona.permissions.includes('*')) return true;
  if (!personaVisibility || personaVisibility.length === 0) return true;
  return personaVisibility.some((visibility) => {
    const role = roleFromVisibility(visibility);
    return role ? canAssumeRole(persona, role) : false;
  });
}

export function getVisibleModules(persona: Persona | null, allModules: WorkbenchScreenModule[]): WorkbenchScreenModule[] {
  if (!persona) return [];
  return allModules.filter((module) => canSeePersonaVisibility(persona, module.personaVisibility) && canAccessRoute(persona, module.routePattern));
}
