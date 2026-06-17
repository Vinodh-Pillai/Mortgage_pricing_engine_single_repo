import { describe, expect, it } from 'vitest';
import { App } from '../../App';
import { matchAppRoute, routeComponentLoaders } from '../../routing/routes';
import { workbenchModules } from '../workbenchShell/WorkbenchShell';

describe('ProductAdmin route registration', () => {
  it('registers exact /admin/products without replacing existing catalog and new-product routes', () => {
    const productAdminModule = workbenchModules.find((module) => module.id === 'product-admin');
    const productManagementModule = workbenchModules.find((module) => module.id === 'product-management');

    expect(App).toBeDefined();
    expect(routeComponentLoaders['product-admin']).toBeDefined();
    expect(productAdminModule?.match('/admin/products')).toBe(true);
    expect(productAdminModule?.match('/admin/products/catalog')).toBe(false);
    expect(productManagementModule?.match('/admin/products/catalog')).toBe(true);
    expect(productManagementModule?.match('/admin/products/management')).toBe(true);
    expect(matchAppRoute('/admin/products').id).toBe('product-admin');
    expect(matchAppRoute('/admin/products/catalog').id).toBe('product-management');
    expect(matchAppRoute('/admin/products/management').id).toBe('product-management-alias');
    expect(matchAppRoute('/admin/products/new').id).toBe('product-management-new');
  });
});
