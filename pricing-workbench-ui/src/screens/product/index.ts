import { lazy } from 'react';
import { createScreenModule } from '../contract/ScreenModule';
import { allVisualStates } from '../shared/MajorFunctionalityPage';
import { productManagementEvidenceTarget } from './ProductManagementScreen';

export const productManagementScreenModule = createScreenModule({
  id: 'product-management',
  label: 'Product Management',
  routePattern: '/admin/products/catalog',
  breadcrumb: 'Product Management',
  screenPackage: 'screens/product',
  dataBoundary: 'product-service catalog APIs',
  stateCoverage: allVisualStates,
  personaVisibility: ['admin', 'pricing-analyst', 'loan-officer'],
  dependencyStatus: 'Product-service catalog and investor eligibility refs govern editable state.',
  adapterStatus: 'Screen is locally available and keeps pricing rules as backend-owned references.',
  evidenceTarget: productManagementEvidenceTarget,
  match: (pathname) => pathname.startsWith('/admin/products/catalog') || pathname.startsWith('/admin/products/new'),
  Component: lazy(() => import('./ProductManagementScreen').then((module) => ({ default: module.ProductManagementScreen }))),
});

export { ProductManagementScreen, productManagementEvidenceTarget } from './ProductManagementScreen';
export default productManagementScreenModule;
