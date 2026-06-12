import '@testing-library/jest-dom/vitest';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from '../../App';

describe('Product catalog manager route', () => {
  beforeEach(() => {
    window.history.pushState({}, '', '/admin/products/catalog');
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = input.toString();
        if (url === '/api/auth/me') {
          return { ok: true, status: 200, json: async () => ({ user: { id: 'test-admin', email: 'admin@example.test', fullName: 'Test Admin', role: 'admin' } }) };
        }
        throw new Error(`Unexpected request ${url}`);
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
    expect(screen.getByRole('link', { name: /Product Management/ })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('table', { name: 'Product catalog records' })).toBeInTheDocument();
    expect(screen.getByText('Purchase product draft')).toBeInTheDocument();
    expect(document.body.textContent).not.toMatch(/rate table|eligibility threshold|fee amount/i);
    await waitFor(() => expect(fetch).toHaveBeenCalledWith('/api/auth/me', expect.objectContaining({ credentials: 'include' })));
  });
});
