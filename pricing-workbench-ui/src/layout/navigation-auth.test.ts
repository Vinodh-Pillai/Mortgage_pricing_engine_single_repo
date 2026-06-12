import { describe, expect, it } from 'vitest';
import { getPersonaById } from '../lib/auth/personas';
import type { WorkbenchScreenModule } from '../screens/workbenchShell/WorkbenchShell';
import { buildNavigationTree, getVisibleModules } from './navigation';

const modules: WorkbenchScreenModule[] = [
  {
    id: 'quote-start',
    label: 'Start quote',
    routePattern: '/quote/start',
    breadcrumb: 'Start',
    screenPackage: 'screens/quickQuote',
    dataBoundary: 'lib/api/quoteRuns',
    stateCoverage: ['ready'],
    personaVisibility: ['borrower', 'loan officer'],
    evidenceTarget: '.local-harness/evidence/PII-25-S02/quote-start.json',
    match: () => true,
  },
  {
    id: 'ops-dashboard',
    label: 'Ops dashboard',
    routePattern: '/ops/dashboard',
    breadcrumb: 'Ops',
    screenPackage: 'screens/opsCases',
    dataBoundary: 'lib/api/opsCases',
    stateCoverage: ['blocked'],
    personaVisibility: ['operations lead'],
    evidenceTarget: '.local-harness/evidence/PII-25-S02/ops.json',
    match: () => false,
  },
];

describe('navigation RBAC filtering', () => {
  it('NavigationTest.filtersModulesByPersona', () => {
    const borrower = getPersonaById('persona-borrower')!;
    const opsLead = getPersonaById('persona-operations-lead')!;
    expect(getVisibleModules(borrower, modules).map((module) => module.id)).toEqual(['quote-start']);
    expect(getVisibleModules(opsLead, modules).map((module) => module.id)).toEqual(['ops-dashboard']);
  });

  it('NavigationTest.buildsVisibleTreeWithPersonaBadges', () => {
    const opsLead = getPersonaById('persona-operations-lead')!;
    const items = buildNavigationTree(modules, 'run-123', opsLead);
    expect(items).toHaveLength(1);
    expect(items[0]).toEqual(expect.objectContaining({ label: 'Ops dashboard', route: '/ops/dashboard', group: 'Operations', badgeCount: 2 }));
  });
});
