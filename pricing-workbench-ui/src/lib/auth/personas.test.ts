import { describe, expect, it } from 'vitest';
import {
  canAccessRoute,
  getEffectivePermissions,
  getPersonaById,
  hasPermission,
  permissionsForRoute,
  roleHierarchy,
  syntheticPersonas,
} from './personas';

describe('synthetic persona RBAC model', () => {
  it('PersonaTest.definesEightSyntheticPersonas', () => {
    expect(syntheticPersonas).toHaveLength(8);
    expect(syntheticPersonas.map((persona) => persona.role).sort()).toEqual([
      'admin',
      'borrower',
      'compliance-officer',
      'governance-reviewer',
      'loan-officer',
      'operations-lead',
      'partner-manager',
      'pricing-analyst',
    ]);
  });

  it('PersonaTest.roleHierarchyAdminHasAll', () => {
    const admin = getPersonaById('persona-admin')!;
    expect(roleHierarchy.admin).toEqual(expect.arrayContaining(['loan-officer', 'borrower', 'pricing-analyst']));
    expect(hasPermission(admin, 'pricing:delete')).toBe(true);
    expect(canAccessRoute(admin, '/service-modules')).toBe(true);
  });

  it('PersonaTest.loanOfficerIncludesBorrower', () => {
    const loanOfficer = getPersonaById('persona-loan-officer')!;
    expect(roleHierarchy['loan-officer']).toContain('borrower');
    expect(getEffectivePermissions(loanOfficer)).toEqual(expect.arrayContaining(['offer:compare', 'scenario:create']));
    expect(hasPermission(loanOfficer, 'offer:compare')).toBe(true);
  });

  it('PersonaTest.sarahFixtureMetadataCoversLoanOfficerWorkflows', () => {
    const sarah = getPersonaById('persona-loan-officer')!;
    expect(sarah.name).toBe('Sarah Mitchell');
    expect(sarah.syntheticFixtureMetadata?.classification).toBe('synthetic-test-only');
    expect(sarah.syntheticFixtureMetadata?.workflowCoverage).toEqual(expect.arrayContaining([
      'pipeline-intake',
      'quick-quote',
      'quote-comparison',
      'request-lock',
      'lock-status',
      'lock-expiry',
      'lock-extension',
      'scenario-analysis',
      'what-if',
    ]));
    expect(sarah.syntheticFixtureMetadata?.missingProductionIntegrations.join(' ')).toMatch(/mocked|fixture-backed|synthetic/i);
    expect(canAccessRoute(sarah, '/pipeline')).toBe(true);
    expect(canAccessRoute(sarah, '/quote/run-123/offers')).toBe(true);
    expect(canAccessRoute(sarah, '/quote/run-123/status')).toBe(true);
    expect(canAccessRoute(sarah, '/quote/run-123/what-if/product-comparison')).toBe(true);
  });

  it('AuthTest.hasPermissionChecksCorrectly', () => {
    const pricingAnalyst = getPersonaById('persona-pricing-analyst')!;
    expect(hasPermission(pricingAnalyst, 'pricing:waterfall')).toBe(true);
    expect(hasPermission(pricingAnalyst, 'ops:manage')).toBe(false);
  });

  it('AuthTest.canAccessRouteRespectsPermissions', () => {
    const borrower = getPersonaById('persona-borrower')!;
    const operationsLead = getPersonaById('persona-operations-lead')!;
    const pricingAnalyst = getPersonaById('persona-pricing-analyst')!;
    const governanceReviewer = getPersonaById('persona-governance-reviewer')!;
    expect(permissionsForRoute('/quote/run-123/offers')?.permissions).toContain('quote:read');
    expect(canAccessRoute(borrower, '/quote/start')).toBe(true);
    expect(canAccessRoute(borrower, '/quickquote')).toBe(true);
    expect(canAccessRoute(borrower, '/ops/dashboard')).toBe(false);
    expect(canAccessRoute(operationsLead, '/ops/dashboard')).toBe(true);
    expect(canAccessRoute(pricingAnalyst, '/scenario-analysis')).toBe(true);
    expect(canAccessRoute(pricingAnalyst, '/rate-sheet-intake')).toBe(true);
    expect(canAccessRoute(operationsLead, '/rate-feed-pipeline')).toBe(true);
    expect(canAccessRoute(pricingAnalyst, '/margin-profitability')).toBe(true);
    expect(canAccessRoute(governanceReviewer, '/governance')).toBe(true);
    expect(canAccessRoute(pricingAnalyst, '/tenant-admin')).toBe(true);
  });
});
