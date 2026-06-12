import '@testing-library/jest-dom/vitest';
import { act, cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import RateFeedOperationsScreen, { exportReplayEvidenceJson, stateForRateFeedOperations } from './RateFeedOperationsScreen';
import { blockedRateFeedOperationsFixture, rateFeedOperationsFixture } from './fixtures';
import { rateFeedOperationsScreenModule } from './index';

afterEach(() => cleanup());

describe('PII-24-S18 rate feed operations screen', () => {
  it('defines the route module and PII-24-S18 evidence target', () => {
    expect(rateFeedOperationsScreenModule.routePattern).toBe('/ops/rate-feeds');
    expect(rateFeedOperationsScreenModule.evidenceTarget).toBe('.local-harness/evidence/PII-24-S18/rate-feed-ops.json');
    expect(rateFeedOperationsScreenModule.match('/ops/rate-feeds')).toBe(true);
    expect(rateFeedOperationsScreenModule.stateCoverage).toEqual(expect.arrayContaining(['load-state', 'workflow-steps', 'row-blockers', 'replay-evidence']));
  });

  it('fetches the operations view with tenant context and story trace id', async () => {
    const fetchImpl = vi.fn(async () => ({ status: 200, json: async () => rateFeedOperationsFixture })) as unknown as typeof fetch;
    render(<RateFeedOperationsScreen tenantContext="tenant-fixture" fetchImpl={fetchImpl} />);

    await act(async () => { await Promise.resolve(); });

    expect(fetchImpl).toHaveBeenCalledWith('/api/v1/ops/rate-feeds', expect.objectContaining({ headers: expect.objectContaining({ 'X-Ui-Trace-Id': 'rf-s18-local-trace', 'X-Tenant-Context': 'tenant-fixture' }) }));
    expect(await screen.findByRole('heading', { name: 'Rate Feed Operations' })).toBeInTheDocument();
  });

  it('renders feed health, workflow refs, disabled rerun, and evidence capture', () => {
    const onEvidenceCapture = vi.fn();
    render(<RateFeedOperationsScreen evidence={rateFeedOperationsFixture} onEvidenceCapture={onEvidenceCapture} />);

    expect(screen.getByRole('heading', { name: 'Feed Health Summary' })).toBeInTheDocument();
    expect(screen.getByText('feed-ref:fixture-primary')).toBeInTheDocument();
    expect(screen.getByText('audit-ref:ingest-fixture')).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: 'Re-run Step' })[0]).toBeDisabled();
    expect(onEvidenceCapture).toHaveBeenCalledWith(expect.objectContaining({ screenId: 'rate-feed-ops', evidenceTarget: '.local-harness/evidence/PII-24-S18/rate-feed-ops.json' }));
  });

  it('shows row blockers with filters, refs, and disabled resolution actions', () => {
    render(<RateFeedOperationsScreen evidence={rateFeedOperationsFixture} />);

    const table = screen.getByRole('table', { name: /Rate grid blockers/i });
    expect(within(table).getByText('row-ref:feed-fixture-042')).toBeInTheDocument();
    expect(within(table).getByText('MISSING RATE')).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Blocker code'), { target: { value: 'INVALID_PRODUCT' } });
    expect(screen.getAllByText('row-ref:feed-fixture-077').length).toBeGreaterThan(0);
    expect(within(table).queryByText('row-ref:feed-fixture-042')).not.toBeInTheDocument();
    fireEvent.click(screen.getByLabelText('Select row-ref:feed-fixture-077'));
    expect(screen.getByRole('button', { name: 'Apply same resolution' })).toBeDisabled();
  });

  it('shows replay diffs, missing dependency blockers, and disabled export', () => {
    render(<RateFeedOperationsScreen evidence={rateFeedOperationsFixture} />);

    expect(screen.getByRole('table', { name: /Replay diff table/i })).toHaveTextContent('row-ref:feed-fixture-042');
    expect(screen.getByText('dependency-ref:live-export-contract-unavailable')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Export Diff/i })).toBeDisabled();
    expect(exportReplayEvidenceJson(rateFeedOperationsFixture)).toContain('PII-24-S18');
  });

  it('renders blocked state without synthesized feed health or blockers', () => {
    render(<RateFeedOperationsScreen evidence={blockedRateFeedOperationsFixture} />);

    expect(screen.getAllByRole('alert')[0]).toHaveTextContent(/BLOCKED UPSTREAM CONTRACT REQUIRED/i);
    expect(screen.getByText(/Feed health refs are required/i)).toBeInTheDocument();
    expect(screen.getByText('No workflow steps supplied.')).toBeInTheDocument();
    expect(stateForRateFeedOperations(blockedRateFeedOperationsFixture)).toBe('blocked');
  });
});
