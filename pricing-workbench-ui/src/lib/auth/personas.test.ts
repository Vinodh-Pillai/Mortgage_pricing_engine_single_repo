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

  it('AuthTest.hasPermissionChecksCorrectly', () => {
    const pricingAnalyst = getPersonaById('persona-pricing-analyst')!;
    expect(hasPermission(pricingAnalyst, 'pricing:waterfall')).toBe(true);
    expect(hasPermission(pricingAnalyst, 'ops:manage')).toBe(false);
  });

  it('AuthTest.canAccessRouteRespectsPermissions', () => {
    const borrower = getPersonaById('persona-borrower')!;
    const operationsLead = getPersonaById('persona-operations-lead')!;
    expect(permissionsForRoute('/quote/run-123/offers')?.permissions).toContain('quote:read');
    expect(canAccessRoute(borrower, '/quote/start')).toBe(true);
    expect(canAccessRoute(borrower, '/ops/dashboard')).toBe(false);
    expect(canAccessRoute(operationsLead, '/ops/dashboard')).toBe(true);
  });
});
