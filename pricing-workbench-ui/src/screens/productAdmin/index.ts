import { lazy } from 'react';
import { createScreenModule } from '../contract/ScreenModule';
import { ProductAdminScreen, productAdminEvidenceTarget, productAdminStateCoverage } from './ProductAdminScreen';

export const productAdminScreenModule = createScreenModule({
  id: 'product-admin',
  label: 'Product Administration',
  routePattern: '/admin/products',
  breadcrumb: 'Product Administration',
  screenPackage: 'screens/productAdmin',
  dataBoundary: 'lib/api/products.fetchProductAdmin and catalog-service admin product/stipulation APIs',
  stateCoverage: productAdminStateCoverage,
  personaVisibility: ['admin', 'pricing-analyst'],
  dependencyStatus: 'Catalog-service admin product, stipulation, mapping, and version-history APIs own persisted write behavior.',
  adapterStatus: 'Local UI renders blocked preview records and does not infer mortgage pricing policy.',
  evidenceTarget: productAdminEvidenceTarget,
  match: (pathname) => pathname === '/admin/products',
  Component: lazy(() => import('./ProductAdminScreen').then((module) => ({ default: module.ProductAdminScreen }))),
});

export { ProductAdminScreen, productAdminEvidenceTarget, productAdminStateCoverage };
export default productAdminScreenModule;
