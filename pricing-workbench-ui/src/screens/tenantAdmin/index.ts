import { lazy } from 'react';
import { createScreenModule } from '../contract/ScreenModule';
import { TenantAdminScreen } from './TenantAdminScreen';

export const tenantAdminEvidenceTarget = '.local-harness/evidence/PII-52-S01/tenant-admin.json';

export const tenantAdminScreenModule = createScreenModule({
  id: 'tenant-admin',
  label: 'Tenant Management',
  routePattern: '/admin/tenants',
  breadcrumb: 'Tenant Management',
  screenPackage: 'screens/tenantAdmin',
  dataBoundary: 'tenant-context-service /api/v1/admin/tenants',
  stateCoverage: ['loading', 'empty', 'blocked', 'needs-attention', 'ready'],
  personaVisibility: ['admin'],
  dependencyStatus: 'Tenant-context-service admin APIs own lifecycle, user counts, branding, and feature flags.',
  adapterStatus: 'Screen renders local preview records when admin APIs are unavailable and keeps backend actions visibly bounded.',
  evidenceTarget: tenantAdminEvidenceTarget,
  match: (pathname) => pathname === '/admin/tenants' || pathname.startsWith('/admin/tenants/'),
  Component: lazy(() => import('./TenantAdminScreen').then((module) => ({ default: module.TenantAdminScreen }))),
});

export { TenantAdminScreen };
export default TenantAdminScreen;
