import { describe, expect, it } from 'vitest';
import { getPersonaById } from '../lib/auth/personas';
import type { WorkbenchScreenModule } from '../screens/workbenchShell/WorkbenchShell';
import { workbenchModules } from '../screens/workbenchShell/WorkbenchShell';
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

  it('NavigationTest.buildsVisibleTreeWithoutNoisyPersonaBadges', () => {
    const opsLead = getPersonaById('persona-operations-lead')!;
    const items = buildNavigationTree(modules, 'run-123', opsLead);
    expect(items).toEqual(expect.arrayContaining([expect.objectContaining({ label: 'Ops dashboard', route: '/ops/dashboard', group: 'Operations', badgeCount: undefined })]));
    expect(items).not.toEqual(expect.arrayContaining([expect.objectContaining({ label: 'Quick Quote', group: 'Pipeline' })]));
  });

  it('NavigationTest.hidesRestrictedNavigationWhenMetadataIsUnavailable', () => {
    expect(buildNavigationTree(modules, 'run-123', null)).toEqual([]);
    expect(buildNavigationTree(modules, 'run-123', 'unknown-role')).toEqual([]);
  });

  it('NavigationTest.filtersLoanOfficerNavigationToQuoteActions', () => {
    const loanOfficer = getPersonaById('persona-loan-officer')!;
    const items = buildNavigationTree(workbenchModules, 'run-123', loanOfficer);
    const labels = items.map((item) => item.label);
    expect(labels).toEqual(expect.arrayContaining(['Pipeline Intake', 'New quote', 'Draft Scenarios', 'Lock Workflow']));
    expect(labels).not.toEqual(expect.arrayContaining(['QuickQuote']));
    expect(items).toEqual(expect.arrayContaining([
      expect.objectContaining({ label: 'Pricing Waterfall', route: '/pricing/waterfall' }),
      expect.objectContaining({ label: 'Quote Journey Map', route: '/journey-map' }),
    ]));
    expect(labels).not.toEqual(expect.arrayContaining(['Product Catalog', 'Rate Sheet Intake', 'Tenant Management', 'User Management']));
  });

  it('NavigationTest.filtersPricingAnalystNavigationToProductRatesheetAndPricingActions', () => {
    const pricingAnalyst = getPersonaById('persona-pricing-analyst')!;
    const labels = buildNavigationTree(workbenchModules, 'run-123', pricingAnalyst).map((item) => item.label);
    expect(labels).toEqual(expect.arrayContaining(['Product Catalog', 'Product Management', 'Rate Sheet Intake', 'Pricing Analysis']));
    expect(labels).not.toEqual(expect.arrayContaining(['Tenant Management', 'User Management']));
  });

  it('NavigationTest.filtersAdminAndOperationsNavigationByConfiguredScopes', () => {
    const admin = getPersonaById('persona-admin')!;
    const operations = getPersonaById('persona-operations-lead')!;
    const adminLabels = buildNavigationTree(workbenchModules, 'run-123', admin).map((item) => item.label);
    const operationLabels = buildNavigationTree(workbenchModules, 'run-123', operations).map((item) => item.label);
    expect(adminLabels).toEqual(expect.arrayContaining(['Product Catalog', 'Tenant Management', 'User Management', 'Feature Flags']));
    expect(operationLabels).toEqual(expect.arrayContaining(['Rate Feed Ops', 'Ops Cases', 'Partner Integrations']));
    expect(operationLabels).not.toEqual(expect.arrayContaining(['User Management', 'Feature Flags']));
  });
});
