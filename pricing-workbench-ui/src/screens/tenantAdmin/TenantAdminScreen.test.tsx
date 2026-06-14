import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { TenantAdminScreen } from './TenantAdminScreen';

describe('TenantAdminScreen', () => {
  it('renders local blocked preview when tenant admin API is unavailable', async () => {
    const fetchImpl = vi.fn().mockRejectedValue(new Error('offline')) as unknown as typeof fetch;

    render(<TenantAdminScreen fetchImpl={fetchImpl} />);

    expect(await screen.findByRole('heading', { name: /Tenant Management/i })).toBeTruthy();
    expect(screen.getByRole('alert')).toHaveTextContent(/local preview records/i);
    expect(screen.getByRole('table', { name: /Tenant management table/i })).toBeInTheDocument();
    expect(screen.getAllByText(/PENDING ACTIVATION/i).length).toBeGreaterThan(0);
  });

  it('supports create modal fields and feature flag affordance', async () => {
    const fetchImpl = vi.fn().mockRejectedValue(new Error('offline')) as unknown as typeof fetch;

    render(<TenantAdminScreen fetchImpl={fetchImpl} />);
    await screen.findByRole('alert');

    fireEvent.click(screen.getByRole('button', { name: /Create Tenant/i }));
    expect(screen.getByRole('dialog', { name: /Tenant profile and branding/i })).toBeInTheDocument();
    const dialog = screen.getByRole('dialog', { name: /Tenant profile and branding/i });
    fireEvent.change(within(dialog).getByLabelText(/^Tenant Name$/i), { target: { value: 'new-lender' } });
    fireEvent.change(within(dialog).getByLabelText(/^Display Name$/i), { target: { value: 'New Lender' } });
    fireEvent.click(within(dialog).getByRole('button', { name: /^Create Tenant$/i }));

    await waitFor(() => expect(screen.queryByRole('dialog', { name: /Tenant profile and branding/i })).not.toBeInTheDocument());
    expect(screen.getByText('new-lender')).toBeInTheDocument();

    fireEvent.click(screen.getAllByRole('button', { name: /Feature Flags/i })[0]);
    expect(await screen.findByRole('dialog', { name: /Feature Flags/i })).toBeInTheDocument();
    expect(screen.getByText(/quick pricer/i)).toBeInTheDocument();
  });
});
