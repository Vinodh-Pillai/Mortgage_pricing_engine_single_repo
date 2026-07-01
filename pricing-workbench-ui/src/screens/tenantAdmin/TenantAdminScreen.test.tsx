import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { TenantAdminScreen } from './TenantAdminScreen';

describe('TenantAdminScreen', () => {
  afterEach(() => cleanup());

  it('renders local blocked preview when tenant admin API is unavailable', async () => {
    const fetchImpl = vi.fn().mockRejectedValue(new Error('offline')) as unknown as typeof fetch;

    render(<TenantAdminScreen fetchImpl={fetchImpl} />);

    expect(await screen.findByRole('heading', { name: /Tenant Management/i })).toBeTruthy();
    expect(screen.getByRole('alert')).toHaveTextContent(/local preview records/i);
    expect(screen.getByRole('table', { name: /Tenant management table/i })).toBeInTheDocument();
    expect(screen.getAllByText(/PENDING ACTIVATION/i).length).toBeGreaterThan(0);
  });

  it('keeps failed create writes blocked without fabricating tenant rows and keeps feature flag affordance', async () => {
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
    expect(screen.queryByText('new-lender')).not.toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent(/offline/i);

    fireEvent.click(screen.getAllByRole('button', { name: /Feature Flags/i })[0]);
    expect(await screen.findByRole('dialog', { name: /Feature Flags/i })).toBeInTheDocument();
    expect(screen.getByText(/quick pricer/i)).toBeInTheDocument();
  });

  it('supports search, status filters, fail-closed lifecycle actions, edit modal, and blocked evidence capture', async () => {
    const fetchImpl = vi.fn().mockRejectedValue(new Error('offline')) as unknown as typeof fetch;
    const onEvidenceCapture = vi.fn();

    render(<TenantAdminScreen fetchImpl={fetchImpl} onEvidenceCapture={onEvidenceCapture} />);
    await screen.findByRole('alert');
    await waitFor(() => expect(onEvidenceCapture).toHaveBeenCalled());
    expect(onEvidenceCapture.mock.calls.at(-1)?.[0]).toMatchObject({ screenId: 'tenant-admin', state: 'blocked' });

    fireEvent.change(screen.getByPlaceholderText('Search tenants'), { target: { value: 'regional' } });
    expect(screen.getByText('regional-lending')).toBeInTheDocument();
    expect(screen.queryByText('acme-mortgage')).not.toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('Status'), { target: { value: 'PENDING_ACTIVATION' } });
    expect(screen.getByText(/No tenants match/i)).toBeInTheDocument();

    fireEvent.change(screen.getByPlaceholderText('Search tenants'), { target: { value: '' } });
    expect(screen.getByText('acme-mortgage')).toBeInTheDocument();
    fireEvent.click(screen.getAllByRole('button', { name: 'Activate' })[0]);
    fireEvent.change(screen.getByLabelText('Status'), { target: { value: 'all' } });
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent(/offline/i));
    expect(screen.getAllByText(/PENDING ACTIVATION/i).length).toBeGreaterThan(0);

    fireEvent.click(screen.getAllByRole('button', { name: 'Suspend' }).find((button) => !button.hasAttribute('disabled'))!);
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent(/offline/i));
    expect(screen.queryByText('SUSPENDED')).not.toBeInTheDocument();
    fireEvent.click(screen.getAllByRole('button', { name: 'Deactivate' }).find((button) => !button.hasAttribute('disabled'))!);
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent(/offline/i));
    expect(screen.queryByText('DEACTIVATED')).not.toBeInTheDocument();

    fireEvent.click(screen.getAllByRole('button', { name: 'Edit' })[0]);
    const dialog = screen.getByRole('dialog', { name: /Tenant profile and branding/i });
    expect(within(dialog).getByLabelText(/^Tenant Name$/i)).toBeDisabled();
    fireEvent.change(within(dialog).getByLabelText(/^Display Name$/i), { target: { value: 'Updated Tenant Preview' } });
    fireEvent.click(within(dialog).getByRole('button', { name: /^Save Tenant$/i }));
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent(/Tenant profile updates require/i));
    expect(screen.queryByText('Updated Tenant Preview')).not.toBeInTheDocument();
  });

  it('renders injected empty evidence as an empty ready state without blocked API fetch', () => {
    const fetchImpl = vi.fn() as unknown as typeof fetch;
    const onEvidenceCapture = vi.fn();

    render(<TenantAdminScreen fetchImpl={fetchImpl} evidence={[]} onEvidenceCapture={onEvidenceCapture} />);

    expect(screen.getByText(/Injected tenant admin evidence/i)).toBeInTheDocument();
    expect(screen.getByText(/No tenants match/i)).toBeInTheDocument();
    expect(fetchImpl).not.toHaveBeenCalled();
    expect(onEvidenceCapture).toHaveBeenCalledWith(expect.objectContaining({ screenId: 'tenant-admin', state: 'empty' }));
  });
});
