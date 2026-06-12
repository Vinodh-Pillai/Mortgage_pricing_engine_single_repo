import { canAccessRoute, canSeePersonaVisibility, getVisibleModules as filterModulesForPersona, type Persona } from '../lib/auth/personas';
import type { WorkbenchScreenModule } from '../screens/workbenchShell/WorkbenchShell';

export type NavigationGroup = 'Quote' | 'Onboarding' | 'Products' | 'Pricing' | 'Locks' | 'Operations' | 'Governance' | 'Partner' | 'Admin';

export type NavigationItem = {
  id: string;
  label: string;
  route: string;
  group: NavigationGroup;
  personas: string[];
  badgeCount?: number;
};

const defaultPersonas = ['loan officer', 'pricing analyst', 'operations lead', 'governance reviewer'];

function routeFor(module: WorkbenchScreenModule, activeRunId?: string | null) {
  return module.routePattern
    .replace(':runId', activeRunId ?? 'run-test')
    .replace(':optionId', 'selected-offer');
}

function groupFor(route: string): NavigationGroup {
  if (route.startsWith('/tenant') || route.startsWith('/admin/tenants')) return 'Onboarding';
  if (route.startsWith('/admin/products')) return 'Products';
  if (route.startsWith('/locks') || route.includes('/lock')) return 'Locks';
  if (route.startsWith('/pricing') || route.startsWith('/custom-rules') || route.includes('waterfall') || route.startsWith('/exceptions')) return 'Pricing';
  if (route.startsWith('/ops') || route.startsWith('/platform') || route.startsWith('/audit')) return 'Operations';
  if (route.startsWith('/compliance') || route.startsWith('/quality')) return 'Governance';
  if (route.startsWith('/partners')) return 'Partner';
  if (route.startsWith('/admin') || route.startsWith('/service-modules') || route.startsWith('/advisory')) return 'Admin';
  return 'Quote';
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

export function getVisibleModules(persona: Persona | null, allModules: WorkbenchScreenModule[]) {
  return filterModulesForPersona(persona, allModules);
}

export function buildNavigationTree(modules: WorkbenchScreenModule[], activeRunId?: string | null, persona?: string | Persona | null): NavigationItem[] {
  const personaObject = typeof persona === 'string' ? null : persona ?? null;
  return modules
    .filter((module) => {
      if (!personaObject) {
        const personas = module.personaVisibility?.length ? module.personaVisibility : defaultPersonas;
        return typeof persona !== 'string' || personas.includes(persona);
      }
      return canSeePersonaVisibility(personaObject, module.personaVisibility) && canAccessRoute(personaObject, routeFor(module, activeRunId));
    })
    .map((module) => {
      const route = routeFor(module, activeRunId);
      const personas = module.personaVisibility?.length ? module.personaVisibility : defaultPersonas;
      return {
        id: module.id,
        label: module.label,
        route,
        group: groupFor(route),
        personas,
        badgeCount: attentionCountFor(module, personaObject),
      };
    });
}

export function navigationGroups(items: NavigationItem[]) {
  return Array.from(new Set(items.map((item) => item.group)));
}
