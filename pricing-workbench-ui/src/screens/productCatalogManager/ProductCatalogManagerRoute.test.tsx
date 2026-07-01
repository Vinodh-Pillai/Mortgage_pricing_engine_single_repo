import '@testing-library/jest-dom/vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from '../../App';

vi.mock('../productManagement/ProductManagementScreen', () => ({
  ProductManagementScreen: () => (
    <section>
      <h1>Product Management</h1>
      <button type="button">Add Product</button>
      <table aria-label="Product catalog records">
        <tbody>
          <tr>
            <td>Purchase product draft</td>
          </tr>
        </tbody>
      </table>
    </section>
  ),
}));

const bffBaseUrl = 'https://pricing-bff.example.test';

describe('Product catalog manager route', () => {
  beforeEach(() => {
    window.history.pushState({}, '', '/admin/products/catalog');
    vi.stubEnv('VITE_BFF_API_BASE_URL', bffBaseUrl);
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = new URL(input.toString(), window.location.origin);
        if (url.href === `${bffBaseUrl}/api/auth/me`) {
          return { ok: true, status: 200, json: async () => ({ user: { id: 'test-admin', email: 'admin@example.test', fullName: 'Test Admin', role: 'admin' } }) };
        }
        throw new Error(`Unexpected request ${url.href}`);
      }),
    );
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it('keeps /admin/products/catalog routed to the new product management screen', async () => {
    render(
      <MemoryRouter initialEntries={['/admin/products/catalog']}>
        <App />
      </MemoryRouter>,
    );

    expect(await screen.findByRole('heading', { name: 'Product Management' }, { timeout: 5000 })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /Open navigation menu/i }));
    expect(screen.getByRole('link', { name: /Product Management, 1 alert/ })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('button', { name: /Add Product/i })).toBeInTheDocument();
    expect(screen.getByRole('table', { name: 'Product catalog records' })).toBeInTheDocument();
    expect(screen.getByText('Purchase product draft')).toBeInTheDocument();
    expect(document.body.textContent).not.toMatch(/rate table|eligibility threshold|fee amount/i);
    await waitFor(() => expect(fetch).toHaveBeenCalledWith(`${bffBaseUrl}/api/auth/me`, expect.objectContaining({ credentials: 'include' })));
  });

  it('keeps /admin/products/management as an alias for product management', async () => {
    render(
      <MemoryRouter initialEntries={['/admin/products/management']}>
        <App />
      </MemoryRouter>,
    );

    expect(await screen.findByRole('heading', { name: 'Product Management' }, { timeout: 5000 })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Add Product/i })).toBeInTheDocument();
  });
});
