import '@testing-library/jest-dom/vitest';
import { act, cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import OpsCasesScreen, { stateForOpsCases } from './OpsCasesScreen';
import { blockedOpsCasesFixture, opsCaseDetailFixture, opsCasesFixture } from './fixtures';
import { opsCasesScreenModule } from './index';

afterEach(() => cleanup());

describe('PII-24-S19 operations cases screen', () => {
  it('defines the route module and PII-24-S19 evidence target', () => {
    expect(opsCasesScreenModule.routePattern).toBe('/ops/dashboard');
    expect(opsCasesScreenModule.evidenceTarget).toBe('.local-harness/evidence/PII-24-S19/ops-cases.json');
    expect(opsCasesScreenModule.match('/ops/dashboard')).toBe(true);
    expect(opsCasesScreenModule.match('/ops/queues')).toBe(true);
    expect(opsCasesScreenModule.match('/ops/cases/ops-lock-blocked')).toBe(true);
    expect(opsCasesScreenModule.match('/ops/escalations')).toBe(true);
    expect(opsCasesScreenModule.stateCoverage).toEqual(expect.arrayContaining(['load-state', 'empty', 'blocked', 'ready']));
  });

  it('fetches operations cases with story trace headers', async () => {
    const fetchImpl = vi.fn(async (input: RequestInfo | URL) => ({ status: 200, json: async () => input.toString().includes('ops-lock-blocked') ? opsCaseDetailFixture : opsCasesFixture })) as unknown as typeof fetch;
    render(<OpsCasesScreen tenantContext="tenant-fixture" fetchImpl={fetchImpl} />);

    await act(async () => { await Promise.resolve(); });

    expect(fetchImpl).toHaveBeenCalledWith('/api/v1/ops/cases', expect.objectContaining({ headers: expect.objectContaining({ 'X-Ui-Trace-Id': 'ops-s06-local-trace', 'X-Tenant-Context': 'ui-preview-tenant' }) }));
    expect(await screen.findByRole('heading', { name: 'Operations Cases' })).toBeInTheDocument();
  });

  it('renders dashboard metrics, charts, and evidence capture', () => {
    const onEvidenceCapture = vi.fn();
    render(<OpsCasesScreen evidence={opsCasesFixture} detailEvidence={opsCaseDetailFixture} onEvidenceCapture={onEvidenceCapture} />);

    expect(screen.getByRole('heading', { name: 'Dashboard metrics and charts' })).toBeInTheDocument();
    expect(screen.getByText('Open Cases')).toBeInTheDocument();
    expect(screen.getByText('Cases by Priority')).toBeInTheDocument();
    expect(onEvidenceCapture).toHaveBeenCalledWith(expect.objectContaining({ screenId: 'ops-cases', evidenceTarget: '.local-harness/evidence/PII-24-S19/ops-cases.json' }));
  });

  it('does not synthesize SLA breach or assignment metrics without backend dashboard metrics', () => {
    const { dashboardMetrics: _dashboardMetrics, ...viewWithoutDashboardMetrics } = opsCasesFixture;
    render(<OpsCasesScreen evidence={viewWithoutDashboardMetrics} detailEvidence={opsCaseDetailFixture} />);

    expect(screen.getByText('No dashboard metrics supplied by operations-service.')).toBeInTheDocument();
    expect(screen.queryByText('SLA Breaches')).not.toBeInTheDocument();
    expect(screen.queryByText('My Assignments')).not.toBeInTheDocument();
  });

  it('shows queues and navigates into filtered cases', () => {
    render(<OpsCasesScreen evidence={opsCasesFixture} detailEvidence={opsCaseDetailFixture} initialPathname="/ops/queues" />);

    expect(screen.getByRole('heading', { name: 'Queues by priority and SLA state' })).toBeInTheDocument();
    fireEvent.click(screen.getAllByRole('button', { name: 'View Queue' })[1]);
    expect(screen.getByLabelText('Queue')).toHaveValue('LOCK');
    expect(screen.getByRole('table', { name: 'Operations cases list' })).toHaveTextContent('ops-lock-blocked');
  });

  it('filters cases and opens detail with disabled backend actions', () => {
    render(<OpsCasesScreen evidence={opsCasesFixture} detailEvidence={opsCaseDetailFixture} initialPathname="/ops/cases" />);

    fireEvent.change(screen.getByLabelText('Priority'), { target: { value: 'P2' } });
    const table = screen.getByRole('table', { name: 'Operations cases list' });
    expect(within(table).getByText('ops-pricing-review')).toBeInTheDocument();
    expect(within(table).queryByText('ops-lock-blocked')).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Priority'), { target: { value: 'all' } });
    fireEvent.click(screen.getAllByRole('button', { name: 'Open case' })[0]);
    expect(screen.getByRole('table', { name: 'Case timeline' })).toHaveTextContent('OpsCaseOpened');
    expect(screen.getByRole('button', { name: 'Assign case' })).toBeDisabled();
    expect(screen.getByText(/Assignment requires operations-service action contract/i)).toBeInTheDocument();
  });

  it('renders escalations and blocked state without local routing or SLA calculation', () => {
    render(<OpsCasesScreen evidence={opsCasesFixture} detailEvidence={opsCaseDetailFixture} initialPathname="/ops/escalations" />);

    expect(screen.getByRole('table', { name: 'Operations case escalations' })).toHaveTextContent('L2');
    expect(screen.getByRole('button', { name: 'Acknowledge Selected' })).toBeDisabled();
    expect(stateForOpsCases(blockedOpsCasesFixture)).toBe('blocked');
  });
});
