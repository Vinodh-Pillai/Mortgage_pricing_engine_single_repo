import { getVisibleModules as filterModulesForPersona, type Persona } from '../lib/auth/personas';
import type { WorkbenchScreenModule } from '../screens/workbenchShell/WorkbenchShell';

export type NavigationGroup = 'Pipeline' | 'Products' | 'Pricing' | 'Rules' | 'Quotes' | 'Scenarios' | 'Operations' | 'Tenants' | 'Admin';
export type NavigationBadgeTone = 'pending' | 'alert';

export type NavigationItem = {
  id: string;
  label: string;
  route: string;
  group: NavigationGroup;
  icon: string;
  personas: string[];
  badgeCount?: number;
  badgeLabel?: string;
  badgeTone?: NavigationBadgeTone;
  sourceModuleId?: string;
};

const defaultPersonas = ['loan officer', 'pricing analyst', 'operations lead', 'governance reviewer'];
const groupOrder: NavigationGroup[] = ['Pipeline', 'Products', 'Pricing', 'Rules', 'Quotes', 'Scenarios', 'Operations', 'Tenants', 'Admin'];

type CapabilityDefinition = {
  id: string;
  label: string;
  group: NavigationGroup;
  icon: string;
  moduleId?: string;
  route?: string;
  badgeTone?: NavigationBadgeTone;
  badgeCount?: number;
  badgeLabel?: string;
};

const capabilityDefinitions: CapabilityDefinition[] = [
  { id: 'pipeline-intake', label: 'Pipeline Intake', group: 'Pipeline', icon: '⌁', moduleId: 'quote-intake', route: '/pipeline', badgeTone: 'pending' },
  { id: 'quick-quote', label: 'Quick Quote', group: 'Pipeline', icon: '⚡', moduleId: 'quote-intake', route: '/quote/start', badgeTone: 'pending', badgeCount: 1, badgeLabel: '1 draft action pending' },
  { id: 'draft-scenarios', label: 'Draft Scenarios', group: 'Pipeline', icon: '◌', moduleId: 'quote-intake', route: '/pipeline?view=drafts', badgeTone: 'pending', badgeCount: 2, badgeLabel: '2 draft scenarios pending' },

  { id: 'product-catalog', label: 'Product Catalog', group: 'Products', icon: '▦', moduleId: 'product-admin', route: '/admin/products', badgeTone: 'alert' },
  { id: 'product-management', label: 'Product Management', group: 'Products', icon: '◫', moduleId: 'product-management', route: '/admin/products/catalog' },
  { id: 'investor-management', label: 'Investor Management', group: 'Products', icon: '◈', moduleId: 'investor-management', route: '/admin/investors', badgeTone: 'pending', badgeCount: 1, badgeLabel: '1 investor setup review pending' },
  { id: 'stipulations', label: 'Stipulations', group: 'Products', icon: '✦', moduleId: 'product-admin', route: '/admin/products?section=stipulations', badgeTone: 'alert' },

  { id: 'pricing-waterfall', label: 'Pricing Waterfall', group: 'Pricing', icon: '≋', moduleId: 'pricing-waterfall' },
  { id: 'rate-feed-pipeline', label: 'Rate Feed Pipeline', group: 'Pricing', icon: '⇄', moduleId: 'rate-feed-pipeline' },
  { id: 'rate-sheet-intake', label: 'Rate Sheet Intake', group: 'Pricing', icon: '▤', moduleId: 'rate-sheet-intake', badgeTone: 'alert' },
  { id: 'pricing-profiles', label: 'Pricing Profiles', group: 'Pricing', icon: '◫', moduleId: 'pricing-profiles', route: '/pricing/profiles', badgeTone: 'pending', badgeCount: 1, badgeLabel: '1 pricing profile setup review pending' },
  { id: 'pricing-analysis', label: 'Pricing Analysis', group: 'Pricing', icon: '⌬', moduleId: 'pricing-analysis' },
  { id: 'margin-profitability', label: 'Margin Profitability', group: 'Pricing', icon: '◍', moduleId: 'margin-profitability', badgeTone: 'alert' },

  { id: 'adjustment-evidence', label: 'Adjustment Evidence', group: 'Rules', icon: '☷', moduleId: 'adjustment-evidence', badgeTone: 'alert' },
  { id: 'exception-concessions', label: 'Exception Concessions', group: 'Rules', icon: '◇', moduleId: 'exception-concessions', badgeTone: 'alert' },
  { id: 'rules-engine', label: 'Rules Engine', group: 'Rules', icon: '▨', moduleId: 'rules-engine', route: '/rules-engine', badgeTone: 'alert' },
  { id: 'custom-rules', label: 'Custom Rules', group: 'Rules', icon: '⎇', moduleId: 'custom-rules', badgeTone: 'alert' },
  { id: 'governance-lifecycle', label: 'Governance Lifecycle', group: 'Rules', icon: '♜', moduleId: 'admin-governance', badgeTone: 'alert' },

  { id: 'quote-offers', label: 'Quote Offers', group: 'Quotes', icon: '◉', moduleId: 'quote-offers' },
  { id: 'quote-detail', label: 'Quote Detail', group: 'Quotes', icon: '⌖', moduleId: 'quote-detail' },
  { id: 'quote-eligibility', label: 'Eligibility', group: 'Quotes', icon: '✓', moduleId: 'quote-eligibility', badgeTone: 'alert' },
  { id: 'lock-workflow', label: 'Lock Workflow', group: 'Quotes', icon: '🔒', moduleId: 'quote-lock', badgeTone: 'alert' },
  { id: 'partner-quotes', label: 'Partner Quotes', group: 'Quotes', icon: '↗', moduleId: 'partner-quotes', badgeTone: 'alert' },

  { id: 'scenario-analysis', label: 'Scenario Analysis', group: 'Scenarios', icon: '◎', moduleId: 'scenario-analysis' },
  { id: 'what-if', label: 'What-If', group: 'Scenarios', icon: '∆', moduleId: 'scenario-analysis', route: '/quote/:runId/what-if/product-comparison' },

  { id: 'rate-feed-ops', label: 'Rate Feed Ops', group: 'Operations', icon: '⇆', moduleId: 'rate-feed-ops', badgeTone: 'alert' },
  { id: 'performance-dashboard', label: 'Performance Dashboard', group: 'Operations', icon: '▧', moduleId: 'performance-dashboard', badgeTone: 'alert' },
  { id: 'ops-cases', label: 'Ops Cases', group: 'Operations', icon: '▣', moduleId: 'ops-cases', badgeTone: 'alert' },
  { id: 'partner-integrations', label: 'Partner Integrations', group: 'Operations', icon: '⟲', moduleId: 'partner-transport', badgeTone: 'alert' },

  { id: 'tenant-home', label: 'Tenant Home', group: 'Tenants', icon: '⌂', moduleId: 'home', route: '/home' },
  { id: 'tenant-onboarding', label: 'Tenant Onboarding', group: 'Tenants', icon: '⧉', moduleId: 'tenant-onboarding', badgeTone: 'alert' },
  { id: 'tenant-management', label: 'Tenant Management', group: 'Tenants', icon: '◧', moduleId: 'tenant-admin', badgeTone: 'alert' },

  { id: 'user-management', label: 'User Management', group: 'Admin', icon: '☻', moduleId: 'user-management', route: '/admin/users', badgeTone: 'pending', badgeCount: 1, badgeLabel: '1 user access review pending' },
  { id: 'feature-flags', label: 'Feature Flags', group: 'Admin', icon: '⚑', moduleId: 'tenant-admin', route: '/admin/tenants?section=feature-flags', badgeTone: 'pending', badgeCount: 2, badgeLabel: '2 feature flags pending review' },
  { id: 'audit-replay', label: 'Audit Replay', group: 'Admin', icon: '◷', moduleId: 'audit-replay', badgeTone: 'alert' },
  { id: 'compliance', label: 'Compliance', group: 'Admin', icon: '⚖', moduleId: 'compliance-evidence', badgeTone: 'alert' },
  { id: 'quality-guardrails', label: 'Quality Guardrails', group: 'Admin', icon: '◆', moduleId: 'quality-dashboard', badgeTone: 'alert' },
  { id: 'ml-advisory', label: 'ML Advisory', group: 'Admin', icon: '✧', moduleId: 'ml-advisory-insights', badgeTone: 'alert' },
];

function routeFor(module: WorkbenchScreenModule, activeRunId?: string | null) {
  return module.routePattern
    .replace(':runId', activeRunId ?? 'run-test')
    .replace(':optionId', 'selected-offer');
}

function resolveRoute(route: string, activeRunId?: string | null) {
  return route.replace(':runId', activeRunId ?? 'run-test').replace(':optionId', 'selected-offer');
}

function fallbackGroupForRoute(route: string): NavigationGroup {
  if (route.startsWith('/pipeline') || route.startsWith('/quote/start')) return 'Pipeline';
  if (route.startsWith('/admin/products')) return 'Products';
  if (route.startsWith('/pricing')) return 'Pricing';
  if (route.startsWith('/exceptions') || route.startsWith('/custom-rules') || route.startsWith('/admin/governance')) return 'Rules';
  if (route.includes('/what-if')) return 'Scenarios';
  if (route.startsWith('/quote')) return 'Quotes';
  if (route.startsWith('/ops') || route.startsWith('/partners') || route.startsWith('/platform')) return 'Operations';
  if (route.startsWith('/tenant') || route.startsWith('/admin/tenants')) return 'Tenants';
  return 'Admin';
}

function attentionCountFor(module: WorkbenchScreenModule, persona?: Persona | null) {
  const hasAttentionState = module.stateCoverage.some((state) => /blocked|unavailable|exception|pending|dlq/i.test(state));
  if (!hasAttentionState) return undefined;
  if (!persona) return 1;
  if (persona.role === 'operations-lead' && (module.routePattern.startsWith('/ops') || module.routePattern.includes('lock'))) return 2;
  if (persona.role === 'compliance-officer' && module.routePattern.startsWith('/compliance')) return 2;
  if (persona.role === 'partner-manager' && module.routePattern.startsWith('/partners')) return 2;
  return 1;
}

function itemForCapability(definition: CapabilityDefinition, moduleById: Map<string, WorkbenchScreenModule>, activeRunId?: string | null, persona?: Persona | null): NavigationItem {
  const module = definition.moduleId ? moduleById.get(definition.moduleId) : undefined;
  const route = definition.route ? resolveRoute(definition.route, activeRunId) : module ? routeFor(module, activeRunId) : '/service-modules';
  const personas = module?.personaVisibility?.length ? module.personaVisibility : defaultPersonas;
  const moduleAttentionCount = module ? attentionCountFor(module, persona) : undefined;
  const badgeCount = definition.badgeCount ?? moduleAttentionCount;
  const badgeTone = definition.badgeTone ?? (badgeCount ? 'alert' : undefined);
  return {
    id: definition.id,
    label: definition.label,
    route,
    group: definition.group,
    icon: definition.icon,
    personas,
    badgeCount,
    badgeTone,
    badgeLabel: definition.badgeLabel ?? (badgeCount ? `${badgeCount} ${badgeTone === 'pending' ? 'pending item' : 'alert'}${badgeCount === 1 ? '' : 's'}` : undefined),
    sourceModuleId: module?.id ?? definition.moduleId,
  };
}

function itemForModule(module: WorkbenchScreenModule, activeRunId?: string | null, persona?: Persona | null): NavigationItem {
  const route = routeFor(module, activeRunId);
  const badgeCount = attentionCountFor(module, persona);
  return {
    id: module.id,
    label: module.label,
    route,
    group: fallbackGroupForRoute(route),
    icon: '•',
    personas: module.personaVisibility?.length ? module.personaVisibility : defaultPersonas,
    badgeCount,
    badgeTone: badgeCount ? 'alert' : undefined,
    badgeLabel: badgeCount ? `${badgeCount} alert${badgeCount === 1 ? '' : 's'}` : undefined,
    sourceModuleId: module.id,
  };
}

export function getVisibleModules(persona: Persona | null, allModules: WorkbenchScreenModule[]) {
  return filterModulesForPersona(persona, allModules);
}

export function buildNavigationTree(modules: WorkbenchScreenModule[], activeRunId?: string | null, persona?: string | Persona | null): NavigationItem[] {
  const personaObject = typeof persona === 'string' ? null : persona ?? null;
  const moduleById = new Map(modules.map((module) => [module.id, module]));
  const representedModuleIds = new Set(capabilityDefinitions.map((definition) => definition.moduleId).filter((id): id is string => Boolean(id)));
  const capabilityItems = capabilityDefinitions.map((definition) => itemForCapability(definition, moduleById, activeRunId, personaObject));
  const registryFallbackItems = modules
    .filter((module) => !representedModuleIds.has(module.id))
    .map((module) => itemForModule(module, activeRunId, personaObject));
  return [...capabilityItems, ...registryFallbackItems];
}

export function navigationGroups(items: NavigationItem[]) {
  const populated = new Set(items.map((item) => item.group));
  return groupOrder.filter((group) => populated.has(group));
}
