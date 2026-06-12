import '@testing-library/jest-dom/vitest';
import { fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ModelVersionGovernanceScreen } from './GovernanceLayout';
import { blockedModelVersionGovernanceView } from './fixtures';

describe('ModelVersionGovernanceScreen', () => {
  beforeEach(() => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        expect(input.toString()).toBe('/api/v1/ml-advisory/governance');
        expect(init?.headers).toEqual(expect.objectContaining({ 'X-Ui-Trace-Id': 'mvg-s26-local-trace' }));
        return { ok: true, status: 200, json: async () => blockedModelVersionGovernanceView };
      }),
    );
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('renders registry, approval, lifecycle, compatibility, and monitoring from API-supplied values', async () => {
    render(<ModelVersionGovernanceScreen />);

    expect(await screen.findByRole('heading', { name: 'Model Version Governance' })).toBeInTheDocument();
    expect(screen.getByRole('table', { name: 'Model registry' })).toHaveTextContent('PENDING APPROVAL');

    fireEvent.click(screen.getByRole('tab', { name: 'Approvals' }));
    expect(screen.getByRole('table', { name: 'Model approvals' })).toHaveTextContent('Performance evidence');
    expect(screen.getByRole('button', { name: 'Approve' })).toBeDisabled();

    fireEvent.click(screen.getByRole('tab', { name: 'Lifecycle' }));
    expect(screen.getByRole('table', { name: 'Model lifecycle' })).toHaveTextContent('Compatibility check required');
    expect(screen.getByRole('button', { name: 'Promote' })).toBeDisabled();

    fireEvent.click(screen.getByRole('tab', { name: 'Compatibility' }));
    expect(screen.getByRole('table', { name: 'Model compatibility matrix' })).toHaveTextContent('pricing-service');
    expect(screen.getByRole('button', { name: 'Run Compatibility Check' })).toBeDisabled();

    fireEvent.click(screen.getByRole('tab', { name: 'Monitoring' }));
    expect(screen.getByRole('list', { name: 'Drift monitoring metrics' })).toHaveTextContent('feature-drift-threshold-ref-required');
    expect(screen.getByRole('list', { name: 'Performance monitoring metrics' })).toHaveTextContent('latency-sla-ref-required');
    expect(screen.getByRole('link', { name: 'View Drift Details' })).toHaveAttribute('href', '/advisory/ml/drift');
    expect(document.body.textContent).not.toMatch(/PSI > 0\.1|p99 >|error rate >|AUC >/i);
  });

  it('validates registration inputs locally without submitting backend-owned actions', async () => {
    render(<ModelVersionGovernanceScreen />);
    await screen.findByRole('heading', { name: 'Model Version Governance' });

    fireEvent.click(screen.getByRole('button', { name: 'Register Model' }));
    const dialog = screen.getByRole('dialog', { name: 'Register Model' });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Validate registration' }));
    expect(within(dialog).getByRole('alert')).toHaveTextContent('Model ID is required');

    fireEvent.change(within(dialog).getByLabelText('Model ID'), { target: { value: 'model-a' } });
    fireEvent.change(within(dialog).getByLabelText('Version'), { target: { value: 'v1' } });
    fireEvent.change(within(dialog).getByLabelText('Artifact refs'), { target: { value: 'artifact-ref' } });
    fireEvent.change(within(dialog).getByLabelText('Metadata'), { target: { value: 'metadata-ref' } });
    fireEvent.change(within(dialog).getByLabelText('Validation'), { target: { value: 'validation-ref' } });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Validate registration' }));
    expect(within(dialog).getByRole('alert')).toHaveTextContent('submission remains disabled until the governance API provides an action contract');
    expect(fetch).toHaveBeenCalledTimes(1);
  });
});
