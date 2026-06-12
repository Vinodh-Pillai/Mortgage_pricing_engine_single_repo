import '@testing-library/jest-dom/vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ProductCatalogManagerScreen } from './CatalogLayout';

describe('ProductCatalogManagerScreen', () => {
  beforeEach(() => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        if (input.toString() !== '/api/v1/products/catalog/manager') throw new Error(`Unexpected request ${input.toString()}`);
        return {
          ok: true,
          status: 200,
          json: async () => ({
            tenantContext: 'ui-preview-tenant',
            dependencyStatus: 'CATALOG_CONTRACTS_UNAVAILABLE',
            areas: [
              {
                areaId: 'draft-products',
                label: 'Product drafts',
                sourceRef: 'catalog-service draft metadata',
                status: 'DRAFT',
                guidance: 'Drafts are visible from the configured catalog response.',
                fields: ['Product name', 'Product owner', 'Version reference'],
                validationMessages: ['Configured draft schema is required before field validation can be marked verified.'],
              },
              {
                areaId: 'retired-products',
                label: 'Retired products',
                sourceRef: 'catalog-service lifecycle metadata',
                status: 'DEPRECATED',
                guidance: 'Retired products are read-only from the configured catalog response.',
                fields: ['Retirement reference'],
                validationMessages: ['Lifecycle transition evidence is required.'],
              },
            ],
            lifecycle: {
              state: 'PENDING_REVIEW',
              actionsDisabled: true,
              actions: ['approve', 'publish', 'rollback'],
              snapshotRefs: ['snapshot-catalog-setup-required', 'event-catalog-contract-required'],
              auditRefs: ['audit-ref-required', 'replay-hash-required'],
              blocker: 'Approval actions stay disabled until catalog-service contracts are configured.',
            },
            events: ['CatalogManagerOpened'],
            fallbackReason: 'Configured catalog-service draft, lifecycle, snapshot, event, and audit contracts are unavailable; fallback records non-secret blocked states only.',
            uiTraceId: 'catalog-manager-local-trace',
          }),
        };
      }),
    );
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('renders catalog areas, detail modal, lifecycle evidence, and validation navigation from backend-supplied metadata', async () => {
    render(<ProductCatalogManagerScreen tenantContext="ui-preview-tenant" />);

    expect(await screen.findByRole('heading', { name: 'Catalog Cockpit' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Product Catalog Manager' })).toBeInTheDocument();
    expect(screen.getByText('catalog-manager-local-trace')).toBeInTheDocument();
    expect(screen.getByRole('list', { name: 'Product catalog manager sections' })).toBeInTheDocument();
    expect(screen.getByText('Product drafts')).toBeInTheDocument();
    expect(screen.getByText('DRAFT')).toBeInTheDocument();
    expect(screen.getByText('DEPRECATED')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Open Product drafts details' }));

    const dialog = screen.getByRole('dialog', { name: 'Product drafts' });
    expect(within(dialog).getByText('draft-products')).toBeInTheDocument();
    expect(within(dialog).getByRole('table', { name: 'Product drafts fields and validation messages' })).toBeInTheDocument();
    expect(within(dialog).getByRole('link', { name: 'Edit Area in governance configuration' })).toHaveAttribute('href', '/admin/governance');

    expect(screen.getByRole('heading', { name: 'Approval, publish, rollback, snapshots, and audit' })).toBeInTheDocument();
    expect(screen.getByText('snapshot-catalog-setup-required')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Validation Runs' })).toHaveAttribute('href', '/admin/governance#validation-runs');
    expect(document.body.textContent).not.toMatch(/rate table|eligibility threshold|fee amount/i);

    await waitFor(() => expect(fetch).toHaveBeenCalledWith('/api/v1/products/catalog/manager', expect.objectContaining({ headers: expect.objectContaining({ 'X-Ui-Trace-Id': 'catalog-manager-local-trace' }) })));
  });
});
