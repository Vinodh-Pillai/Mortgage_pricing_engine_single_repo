import '@testing-library/jest-dom/vitest';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ProductManagementScreen } from './ProductManagementScreen';

afterEach(() => cleanup());

describe('ProductManagementScreen', () => {
  it('shows Add Product and opens a draft detail after save', async () => {
    const fetchImpl = vi.fn(async () => {
      throw new Error('offline preview');
    }) as unknown as typeof fetch;

    render(<ProductManagementScreen fetchImpl={fetchImpl} />);

    const addProductButton = await screen.findByRole('button', { name: /Add Product/i });
    expect(addProductButton).toBeInTheDocument();

    fireEvent.click(addProductButton);
    const addDialog = screen.getByRole('dialog', { name: /Add Product/i });
    expect(addDialog).toBeVisible();

    fireEvent.change(within(addDialog).getByLabelText(/^Code$/i), { target: { value: 'E2E_ROUTE' } });
    fireEvent.change(within(addDialog).getByLabelText(/^Name$/i), { target: { value: 'E2E route product' } });
    fireEvent.click(within(addDialog).getByRole('button', { name: /^Save$/i }));

    await waitFor(() => expect(screen.getByRole('dialog', { name: /E2E route product/i })).toBeVisible());
    expect(screen.getAllByText('E2E_ROUTE').length).toBeGreaterThan(0);
  }, 30000);

  it('keeps loading state non-interactive and exposes keyboard accessible product details', async () => {
    const loadingFetch = vi.fn(async () => {
      throw new Error('offline preview');
    }) as unknown as typeof fetch;
    const loadingRender = render(<ProductManagementScreen fetchImpl={loadingFetch} />);
    expect(screen.getByRole('status')).toHaveTextContent(/Loading products/i);
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
    loadingRender.unmount();

    const fetchImpl = vi.fn(async () => {
      throw new Error('offline preview');
    }) as unknown as typeof fetch;
    render(<ProductManagementScreen fetchImpl={fetchImpl} />);

    const productCard = await screen.findByRole('button', { name: /Open LoanPass setup product details/i });
    productCard.focus();
    fireEvent.keyDown(productCard, { key: 'Enter' });
    expect(screen.getByRole('dialog', { name: /LoanPass setup product/i })).toBeVisible();

    fireEvent.click(screen.getByRole('button', { name: /Close LoanPass setup product details/i }));
    await waitFor(() => expect(screen.queryByRole('dialog', { name: /LoanPass setup product/i })).not.toBeInTheDocument());

    fireEvent.keyDown(productCard, { key: ' ' });
    expect(screen.getByRole('dialog', { name: /LoanPass setup product/i })).toBeVisible();
    fireEvent.keyDown(window, { key: 'Escape' });
    await waitFor(() => expect(screen.queryByRole('dialog', { name: /LoanPass setup product/i })).not.toBeInTheDocument());
  });
});
