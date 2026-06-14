import '@testing-library/jest-dom/vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { TenantHomeScreen } from './TenantHomeScreen';
import { tenantHomeScreenModule } from './index';
import type { AuthorizedProduct, TenantHomeTenantContext } from '../../lib/api/tenantHome';

afterEach(() => {
  cleanup();
  window.localStorage.clear();
});

const tenants: TenantHomeTenantContext[] = [
  { tenantId: 'tenant-a', tenantName: 'Tenant A', status: 'ACTIVE', userId: 'user-1' },
  { tenantId: 'tenant-b', tenantName: 'Tenant B', status: 'ACTIVE', userId: 'user-1' },
];

const products: AuthorizedProduct[] = Array.from({ length: 22 }, (_, index) => ({
  productCode: `PROD_${String(index + 1).padStart(2, '0')}`,
  productName: index % 2 === 0 ? `Conventional Product ${index + 1}` : `FHA Product ${index + 1}`,
  productType: index % 2 === 0 ? 'CONVENTIONAL' : 'FHA',
  investorCode: index % 3 === 0 ? 'FNMA' : 'GNMA',
  channelCode: index % 2 === 0 ? 'RETAIL' : 'WHOLESALE',
  status: index === 21 ? 'PENDING' : 'ACTIVE',
  lastUpdated: 'backend-ref:last-updated',
}));

describe('TenantHomeTest', () => {
  it('shows authorized products for a tenant with filters and pagination', async () => {
    render(<TenantHomeScreen tenants={tenants} initialProducts={products} userId="user-1" />);

    expect(await screen.findByRole('heading', { name: 'Authorized products' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Product grid' })).toBeInTheDocument();
    expect(screen.getByText('21 total')).toBeInTheDocument();
    expect(screen.getByText('PROD_01')).toBeInTheDocument();
    expect(screen.getByText('Page 1 of 2')).toBeInTheDocument();
  });

  it('filters products by type and clears filters deterministically', async () => {
    render(<TenantHomeScreen tenants={tenants} initialProducts={products} userId="user-1" />);

    fireEvent.click(await screen.findByLabelText('FHA'));

    await waitFor(() => expect(screen.getByText('10 total')).toBeInTheDocument());
    expect(screen.queryByText('PROD_01')).not.toBeInTheDocument();
    expect(screen.getByText('PROD_02')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Clear Filters' }));

    await waitFor(() => expect(screen.getByText('22 total')).toBeInTheDocument());
  });

  it('switches tenant context and persists localStorage', async () => {
    render(<TenantHomeScreen tenants={tenants} initialProducts={products} userId="user-1" />);

    fireEvent.change(await screen.findByLabelText('Switch tenant context'), { target: { value: 'tenant-b' } });

    await waitFor(() => {
      expect(window.localStorage.getItem('wcpe:tenantContext:user-1')).toContain('tenant-b');
    });
    expect(screen.getByRole('heading', { name: 'Tenant B' })).toBeInTheDocument();
  });

  it('paginates products when status filter includes all products', async () => {
    render(<TenantHomeScreen tenants={tenants} initialProducts={products} userId="user-1" />);

    fireEvent.click(await screen.findByLabelText('ALL'));

    await waitFor(() => expect(screen.getByText('22 total')).toBeInTheDocument());
    expect(screen.getByText('Page 1 of 2')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Next' }));

    await waitFor(() => expect(screen.getByText('Page 2 of 2')).toBeInTheDocument());
    expect(screen.getByText('PROD_22')).toBeInTheDocument();
  });

  it('quick actions navigate or record comparison intent', async () => {
    const onNavigate = vi.fn();
    render(<TenantHomeScreen tenants={tenants} initialProducts={products.slice(0, 1)} userId="user-1" onNavigate={onNavigate} />);

    fireEvent.click(await screen.findByRole('button', { name: 'View Details' }));
    expect(onNavigate).toHaveBeenCalledWith('/admin/products/catalog/PROD_01');

    fireEvent.click(screen.getByRole('button', { name: 'Create Quote' }));
    expect(onNavigate).toHaveBeenCalledWith('/pipeline?product=PROD_01');

    fireEvent.click(screen.getByRole('button', { name: 'Compare' }));
    expect(screen.getByText('Added PROD_01 to comparison tray.')).toBeInTheDocument();
  });

  it('registers the tenant home screen module contract', () => {
    expect(tenantHomeScreenModule.id).toBe('tenant-home');
    expect(tenantHomeScreenModule.routePattern).toBe('/home');
    expect(tenantHomeScreenModule.evidenceTarget).toBe('.local-harness/evidence/PII-51-S01/tenant-home-screen.json');
    expect(tenantHomeScreenModule.match('/home')).toBe(true);
    expect(tenantHomeScreenModule.stateCoverage).toEqual(expect.arrayContaining(['loading', 'empty', 'blocked', 'needs-attention', 'ready']));
  });
});
