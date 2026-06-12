import '@testing-library/jest-dom/vitest';
import { act, cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import PerformanceDashboardScreen, { exportPerformanceDashboardJson, stateForPerformanceDashboard } from './PerformanceDashboardScreen';
import { blockedPerformanceDashboardFixture, performanceDashboardFixture } from './fixtures';
import { performanceDashboardScreenModule } from './index';

afterEach(() => cleanup());

describe('PII-24-S20 performance dashboard screen', () => {
  it('defines the route module and PII-24-S20 evidence target', () => {
    expect(performanceDashboardScreenModule.routePattern).toBe('/ops/performance');
    expect(performanceDashboardScreenModule.evidenceTarget).toBe('.local-harness/evidence/PII-24-S20/performance-dashboard.json');
    expect(performanceDashboardScreenModule.match('/ops/performance')).toBe(true);
    expect(performanceDashboardScreenModule.stateCoverage).toEqual(expect.arrayContaining(['service-groups', 'freshness', 'blocked-evidence', 'recovery-owner']));
  });

  it('fetches the dashboard with tenant context and story trace id', async () => {
    const fetchImpl = vi.fn(async () => ({ status: 200, json: async () => performanceDashboardFixture })) as unknown as typeof fetch;
    render(<PerformanceDashboardScreen tenantContext="tenant-fixture" fetchImpl={fetchImpl} />);

    await act(async () => { await Promise.resolve(); });

    expect(fetchImpl).toHaveBeenCalledWith('/api/v1/ops/performance', expect.objectContaining({ headers: expect.objectContaining({ 'X-Ui-Trace-Id': 'perf-s20-local-trace', 'X-Tenant-Context': 'tenant-fixture' }) }));
    expect(await screen.findByRole('heading', { name: 'Performance Dashboard' })).toBeInTheDocument();
  });

  it('renders signal groups with service, tenant, correlation, freshness, and evidence refs', () => {
    const onEvidenceCapture = vi.fn();
    render(<PerformanceDashboardScreen evidence={performanceDashboardFixture} onEvidenceCapture={onEvidenceCapture} />);

    expect(screen.getAllByText('Pricing Service').length).toBeGreaterThan(0);
    expect(screen.getByText('corr-ref:pricing-latency-window')).toBeInTheDocument();
    expect(screen.getAllByText('STALE').length).toBeGreaterThan(0);
    expect(screen.getByText('metric-ref:pricing-latency-p95')).toBeInTheDocument();
    expect(screen.getByText('trace-ref:pricing-latency')).toBeInTheDocument();
    expect(onEvidenceCapture).toHaveBeenCalledWith(expect.objectContaining({ screenId: 'performance-dashboard', evidenceTarget: '.local-harness/evidence/PII-24-S20/performance-dashboard.json' }));
  });

  it('renders severity badges, recovery owner, runbook fallback, and disabled actions', () => {
    render(<PerformanceDashboardScreen evidence={performanceDashboardFixture} />);

    const impacts = screen.getByRole('table', { name: /Performance impacts/i });
    expect(within(impacts).getByText('LATENCY_SPIKE')).toBeInTheDocument();
    expect(within(impacts).getByText('HIGH')).toBeInTheDocument();
    expect(within(impacts).getByText('sre-pricing')).toBeInTheDocument();
    expect(within(impacts).getByText('Runbook not configured')).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: 'Assign to Me' })[0]).toBeDisabled();
    expect(screen.getAllByRole('button', { name: 'Create Case' })[0]).toBeDisabled();
  });

  it('groups evidence links by service and opens them in a new tab', () => {
    render(<PerformanceDashboardScreen evidence={performanceDashboardFixture} />);

    const pricingLinks = screen.getByRole('list', { name: /pricing-service evidence links/i });
    expect(within(pricingLinks).getByText('Pricing latency dashboard')).toBeInTheDocument();
    expect(within(pricingLinks).getAllByRole('link', { name: 'Open in New Tab' })[0]).toHaveAttribute('target', '_blank');
  });

  it('shows blockers with owner/message and ops case navigation when available', () => {
    const onNavigate = vi.fn();
    render(<PerformanceDashboardScreen evidence={performanceDashboardFixture} onNavigate={onNavigate} />);

    const blockers = screen.getAllByRole('table', { name: /Performance blockers/i }).find((table) => within(table).queryByText('RUNBOOK_OWNER_REQUIRED'))!;
    expect(within(blockers).getByText('RUNBOOK_OWNER_REQUIRED')).toBeInTheDocument();
    expect(within(blockers).getByText('sre-lead')).toBeInTheDocument();
    fireEvent.click(within(blockers).getAllByRole('button', { name: 'View in Ops Cases' })[0]);
    expect(onNavigate).toHaveBeenCalledWith('/ops/cases/ops-pricing-review');
  });

  it('renders blocked state without synthesized metrics', () => {
    render(<PerformanceDashboardScreen evidence={blockedPerformanceDashboardFixture} />);

    expect(screen.getAllByRole('alert')[0]).toHaveTextContent(/BLOCKED OBSERVABILITY CONTRACT REQUIRED/i);
    expect(screen.getByText(/No signal groups were supplied/i)).toBeInTheDocument();
    expect(stateForPerformanceDashboard(blockedPerformanceDashboardFixture)).toBe('blocked');
    expect(exportPerformanceDashboardJson(performanceDashboardFixture)).toContain('PII-24-S20');
  });
});
