import { lazy } from 'react';
import { createScreenModule } from '../contract/ScreenModule';
import { allVisualStates } from '../shared/MajorFunctionalityPage';
import { tenantOnboardingEvidenceTarget } from './TenantOnboardingScreen';

export const tenantOnboardingScreenModule = createScreenModule({
  id: 'tenant-onboarding',
  label: 'Tenant Onboarding',
  routePattern: '/tenant/onboarding',
  breadcrumb: 'Tenant Onboarding',
  screenPackage: 'screens/tenant',
  dataBoundary: 'tenant-service tenant onboarding APIs',
  stateCoverage: allVisualStates,
  personaVisibility: ['admin', 'operations-lead'],
  dependencyStatus: 'Tenant-service metadata is required for full launch. The screen shows blocked and attention states when refs are unavailable.',
  adapterStatus: 'Local UI renders progressive sections and evidence refs without inventing tenant values.',
  evidenceTarget: tenantOnboardingEvidenceTarget,
  match: (pathname) => pathname.startsWith('/tenant/onboarding') || pathname.startsWith('/admin/tenants/new'),
  Component: lazy(() => import('./TenantOnboardingScreen').then((module) => ({ default: module.TenantOnboardingScreen }))),
});

export { TenantOnboardingScreen, tenantOnboardingEvidenceTarget } from './TenantOnboardingScreen';
export default tenantOnboardingScreenModule;
