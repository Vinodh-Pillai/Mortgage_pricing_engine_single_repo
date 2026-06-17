import '@testing-library/jest-dom/vitest';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ProductAdminScreen } from './ProductAdminScreen';

describe('ProductAdminScreen', () => {
  afterEach(() => cleanup());

  it('renders blocked local preview with products, stipulations, mapping matrix, and no pricing policy constants when API is unavailable', async () => {
    const fetchImpl = vi.fn().mockRejectedValue(new Error('offline')) as unknown as typeof fetch;

    render(<ProductAdminScreen fetchImpl={fetchImpl} />);

    expect(await screen.findByRole('heading', { name: 'Product Administration' })).toBeTruthy();
    expect(screen.getByRole('alert')).toHaveTextContent(/Product admin APIs need setup/i);
    expect(screen.getByRole('table', { name: /Product administration table/i })).toBeTruthy();
    expect(screen.getByText('PRODUCT_SETUP_REQUIRED')).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Stipulations' }));
    expect(screen.getByRole('table', { name: /Stipulation library table/i })).toBeTruthy();
    expect(screen.getByText('STIP_SETUP_REQUIRED')).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Mappings' }));
    expect(screen.getByRole('table', { name: /Product stipulation mapping matrix/i })).toBeTruthy();
    expect(screen.getByLabelText(/Mapped/i)).toBeChecked();
    expect(document.body.textContent).not.toMatch(/rate table|fee amount|eligibility threshold/i);
  });

  it('supports local product and stipulation create flows and mapping toggle while preserving backend ownership wording', async () => {
    const fetchImpl = vi.fn().mockRejectedValue(new Error('offline')) as unknown as typeof fetch;

    render(<ProductAdminScreen fetchImpl={fetchImpl} />);
    await screen.findByRole('alert');

    fireEvent.click(screen.getByRole('button', { name: /Create Product/i }));
    let dialog = screen.getByRole('dialog', { name: /Product definition form/i });
    fireEvent.change(within(dialog).getByLabelText(/Product Code/i), { target: { value: 'admin_demo' } });
    fireEvent.change(within(dialog).getByLabelText(/Product Name/i), { target: { value: 'Admin demo product' } });
    fireEvent.change(within(dialog).getByLabelText(/Min Loan Amount/i), { target: { value: '100000' } });
    fireEvent.change(within(dialog).getByLabelText(/Max Loan Amount/i), { target: { value: '200000' } });
    fireEvent.change(within(dialog).getByLabelText(/Min FICO/i), { target: { value: '700' } });
    fireEvent.change(within(dialog).getByLabelText(/Max LTV/i), { target: { value: '80' } });
    fireEvent.change(within(dialog).getByLabelText(/Max DTI/i), { target: { value: '36' } });
    fireEvent.click(within(dialog).getByRole('button', { name: /^Create Product$/i }));
    await waitFor(() => expect(screen.queryByRole('dialog', { name: /Product definition form/i })).not.toBeInTheDocument());
    expect(screen.getByText('ADMIN_DEMO')).toBeInTheDocument();
    expect(screen.getByText(/100000 - 200000/i)).toBeInTheDocument();
    expect(screen.getByText(/FICO 700 \/ LTV 80 \/ DTI 36/i)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /Edit product preview for ADMIN_DEMO/i }));
    expect(screen.getByText(/Local update preview recorded/i)).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Stipulations' }));
    fireEvent.click(screen.getByRole('button', { name: /Create Stipulation/i }));
    dialog = screen.getByRole('dialog', { name: /Stipulation form/i });
    fireEvent.change(within(dialog).getByLabelText(/Stipulation Code/i), { target: { value: 'doc_required' } });
    fireEvent.change(within(dialog).getByLabelText(/Stipulation Name/i), { target: { value: 'Documentation required' } });
    fireEvent.change(within(dialog).getByLabelText(/Validation Rule JSON/i), { target: { value: '{"source":"backend"}' } });
    fireEvent.click(within(dialog).getByRole('button', { name: /^Create Stipulation$/i }));
    await waitFor(() => expect(screen.queryByRole('dialog', { name: /Stipulation form/i })).not.toBeInTheDocument());
    expect(screen.getByText('DOC_REQUIRED')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /Edit stipulation preview for DOC_REQUIRED/i }));
    expect(screen.getAllByText(/Local update preview recorded/i).length).toBeGreaterThan(0);

    fireEvent.click(screen.getByRole('button', { name: 'Mappings' }));
    const newMapping = screen.getAllByLabelText(/Not mapped/i)[0];
    fireEvent.click(newMapping);
    expect(newMapping).toBeChecked();
    expect(screen.getByText(/catalog-service mapping persistence/i)).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: /Stipulations/i }));
    fireEvent.click(screen.getByRole('button', { name: /Delete stipulation preview for DOC_REQUIRED/i }));
    expect(screen.queryByText('DOC_REQUIRED')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /Products/i }));
    fireEvent.click(screen.getByRole('button', { name: /Delete product preview for ADMIN_DEMO/i }));
    expect(screen.queryByText('ADMIN_DEMO')).not.toBeInTheDocument();
  });

  it('validates duplicate and invalid local product and stipulation drafts without inventing policy defaults', async () => {
    const fetchImpl = vi.fn().mockRejectedValue(new Error('offline')) as unknown as typeof fetch;

    render(<ProductAdminScreen fetchImpl={fetchImpl} />);
    await screen.findByRole('alert');

    fireEvent.click(screen.getByRole('button', { name: /Create Product/i }));
    let dialog = screen.getByRole('dialog', { name: /Product definition form/i });
    const productForm = dialog.querySelector('form')!;
    fireEvent.submit(productForm);
    expect(await screen.findByText(/Product code and name are required/i)).toBeInTheDocument();

    fireEvent.change(within(dialog).getByLabelText(/Product Code/i), { target: { value: 'PRODUCT_SETUP_REQUIRED' } });
    fireEvent.change(within(dialog).getByLabelText(/Product Name/i), { target: { value: 'Duplicate' } });
    fireEvent.submit(productForm);
    expect(await screen.findByText(/Product code must be unique/i)).toBeInTheDocument();

    fireEvent.change(within(dialog).getByLabelText(/Product Code/i), { target: { value: 'bad_num' } });
    fireEvent.change(within(dialog).getByLabelText(/Min Loan Amount/i), { target: { value: 'not-a-number' } });
    fireEvent.submit(productForm);
    expect(await screen.findByText(/minLoanAmount must be numeric/i)).toBeInTheDocument();
    fireEvent.click(within(dialog).getByRole('button', { name: 'Cancel' }));

    fireEvent.click(screen.getByRole('button', { name: 'Stipulations' }));
    fireEvent.click(screen.getByRole('button', { name: /Create Stipulation/i }));
    dialog = screen.getByRole('dialog', { name: /Stipulation form/i });
    const stipulationForm = dialog.querySelector('form')!;
    fireEvent.submit(stipulationForm);
    expect(await screen.findByText(/Stipulation code and name are required/i)).toBeInTheDocument();

    fireEvent.change(within(dialog).getByLabelText(/Stipulation Code/i), { target: { value: 'STIP_SETUP_REQUIRED' } });
    fireEvent.change(within(dialog).getByLabelText(/Stipulation Name/i), { target: { value: 'Duplicate stip' } });
    fireEvent.submit(stipulationForm);
    expect(await screen.findByText(/Stipulation code must be unique/i)).toBeInTheDocument();

    fireEvent.change(within(dialog).getByLabelText(/Stipulation Code/i), { target: { value: 'json_bad' } });
    fireEvent.change(within(dialog).getByLabelText(/Validation Rule JSON/i), { target: { value: '[]' } });
    fireEvent.submit(stipulationForm);
    expect(await screen.findByText(/Validation rule must be a JSON object/i)).toBeInTheDocument();
  });

  it('renders backend-ready and empty states and advances lifecycle without crossing persisted backend boundaries', async () => {
    const backendView = {
      tenantContext: 'backend-tenant',
      dependencyStatus: 'READY',
      uiTraceId: 'trace-backend',
      fallbackReason: '',
      lifecycle: ['DRAFT', 'REVIEW', 'PUBLISHED', 'DEPRECATED'],
      pricingRuleSets: [],
      products: [],
      stipulations: [],
      mappings: [],
    };
    const fetchImpl = vi.fn().mockResolvedValue({ status: 200, json: async () => backendView }) as unknown as typeof fetch;

    render(<ProductAdminScreen fetchImpl={fetchImpl} tenantContext="ignored-local" />);

    expect(await screen.findByText('READY')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent(/No product definitions/i);
    fireEvent.click(screen.getByRole('button', { name: 'Stipulations' }));
    expect(screen.getByRole('table', { name: /Stipulation library table/i })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Mappings' }));
    expect(screen.getByRole('table', { name: /Product stipulation mapping matrix/i })).toBeInTheDocument();
  });

  it('advances product lifecycle preview through terminal deprecated state', async () => {
    const fetchImpl = vi.fn().mockRejectedValue(new Error('offline')) as unknown as typeof fetch;

    render(<ProductAdminScreen fetchImpl={fetchImpl} />);
    await screen.findByRole('alert');

    const advanceButton = screen.getByRole('button', { name: /Advance status/i });
    expect(advanceButton).not.toBeDisabled();
    fireEvent.click(advanceButton);
    expect(screen.getByText(/Local lifecycle preview updated/i)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /Advance status/i }));
    expect(screen.getByText(/Version 3/i)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /Advance status/i }));
    expect(screen.getByText(/Version 4/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Advance status/i })).toBeDisabled();
  });
});
