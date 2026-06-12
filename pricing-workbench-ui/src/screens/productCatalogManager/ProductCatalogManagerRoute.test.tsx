import '@testing-library/jest-dom/vitest';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from '../../App';

function catalogManagerFixture() {
  return {
    tenantContext: 'ui-preview-tenant',
    dependencyStatus: 'CATALOG_CONTRACTS_UNAVAILABLE',
    areas: [
      {
        areaId: 'draft-products',
        label: 'Product drafts',
        sourceRef: 'catalog-service draft metadata',
        status: 'BLOCKED',
        guidance: 'Drafts are visible, but configured product draft contracts are required before publish.',
        fields: ['Product name', 'Product owner', 'Borrower need', 'Version reference'],
        validationMessages: ['Configured draft schema is required before field validation can be marked verified.'],
      },
    ],
    lifecycle: {
      state: 'REVIEW_BLOCKED',
      actionsDisabled: true,
      actions: ['approve', 'publish', 'rollback'],
      snapshotRefs: ['snapshot-catalog-contract-required'],
      auditRefs: ['audit-ref-required', 'replay-hash-required'],
      blocker: 'Approval, publish, rollback, snapshot, event, and audit actions stay disabled until catalog-service contracts are configured.',
    },
    events: ['CatalogManagerOpened'],
    fallbackReason: 'Configured catalog-service draft, lifecycle, snapshot, event, and audit contracts are unavailable; fallback records non-secret blocked states only.',
    uiTraceId: 'catalog-manager-local-trace',
  };
}

describe('Product catalog manager route', () => {
  beforeEach(() => {
    window.history.pushState({}, '', '/admin/products/catalog');
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = input.toString();
        if (url === '/api/ui/health') {
          return { ok: true, status: 200, json: async () => ({ service: 'pricing-workbench', status: 'AVAILABLE', ready: true, dependencyStatus: 'Connected services need setup', dependencies: [] }) };
        }
        if (url === '/api/v1/tenants/ui-preview-tenant/quote-runs/intake-metadata') {
          return { ok: true, status: 200, json: async () => ({ fieldGroups: [] }) };
        }
        if (url === '/api/v1/products/catalog/manager') {
          return { ok: true, status: 200, json: async () => catalogManagerFixture() };
        }
        throw new Error(`Unexpected request ${url}`);
      }),
    );
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it('keeps /admin/products/catalog routed to the extracted manager screen', async () => {
    render(<App />);

    expect(screen.getByRole('link', { name: /Product catalog manager/ })).toHaveAttribute('aria-current', 'page');
    expect(await screen.findByRole('heading', { name: 'Catalog sections and validation' })).toBeInTheDocument();
    expect(screen.getByText('Product drafts')).toBeInTheDocument();
    expect(screen.getByText('snapshot-catalog-contract-required')).toBeInTheDocument();
    expect(document.body.textContent).not.toMatch(/rate table|eligibility threshold|fee amount/i);
    await waitFor(() => expect(fetch).toHaveBeenCalledWith('/api/v1/products/catalog/manager', expect.objectContaining({ headers: expect.objectContaining({ 'X-Ui-Trace-Id': 'catalog-manager-local-trace' }) })));
  });
});
