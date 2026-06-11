import type { WorkbenchScreenModule } from '../screens/workbenchShell/WorkbenchShell';

export type NavigationGroup = 'Quote' | 'Pricing' | 'Operations' | 'Governance' | 'Partner' | 'Admin';

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
  if (route.startsWith('/pricing') || route.startsWith('/custom-rules') || route.includes('waterfall')) return 'Pricing';
  if (route.startsWith('/ops') || route.startsWith('/platform') || route.startsWith('/audit')) return 'Operations';
  if (route.startsWith('/compliance') || route.startsWith('/quality')) return 'Governance';
  if (route.startsWith('/partners')) return 'Partner';
  if (route.startsWith('/admin') || route.startsWith('/service-modules') || route.startsWith('/advisory')) return 'Admin';
  return 'Quote';
}

export function buildNavigationTree(modules: WorkbenchScreenModule[], activeRunId?: string | null, persona?: string): NavigationItem[] {
  return modules
    .map((module) => {
      const route = routeFor(module, activeRunId);
      const personas = module.personaVisibility?.length ? module.personaVisibility : defaultPersonas;
      return {
        id: module.id,
        label: module.label,
        route,
        group: groupFor(route),
        personas,
        badgeCount: module.stateCoverage.some((state) => /blocked|unavailable/i.test(state)) ? 1 : undefined,
      };
    })
    .filter((item) => !persona || item.personas.includes(persona));
}

export function navigationGroups(items: NavigationItem[]) {
  return Array.from(new Set(items.map((item) => item.group)));
}
