import { lazy } from 'react';
import { createScreenModule } from '../contract/ScreenModule';
import type { ScreenVisualState } from '../contract/ScreenProps';
import { TenantHomeScreen } from './TenantHomeScreen';
import { tenantHomeEvidenceTarget } from '../../lib/api/tenantHome';

export const tenantHomeStateCoverage: ScreenVisualState[] = ['loading', 'empty', 'blocked', 'needs-attention', 'ready'];

export const tenantHomeScreenModule = createScreenModule({
  id: 'tenant-home',
  label: 'Home',
  routePattern: '/home',
  breadcrumb: 'Home',
  screenPackage: 'screens/tenantHome',
  dataBoundary: 'lib/api/tenantHome and catalog-service tenant product authorization APIs',
  stateCoverage: tenantHomeStateCoverage,
  personaVisibility: ['loan-officer', 'pricing-analyst', 'borrower', 'admin'],
  dependencyStatus: 'Catalog-service tenant product authorization APIs own persisted product visibility and rate indicators.',
  adapterStatus: 'Screen renders bounded local preview products when backend tenant-product APIs are unavailable.',
  evidenceTarget: tenantHomeEvidenceTarget,
  match: (pathname) => pathname === '/home' || pathname.startsWith('/home/'),
  Component: lazy(() => import('./TenantHomeScreen').then((module) => ({ default: module.TenantHomeScreen }))),
});

export { TenantHomeScreen, tenantHomeEvidenceTarget };
export default TenantHomeScreen;
