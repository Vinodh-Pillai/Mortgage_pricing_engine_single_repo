import '@testing-library/jest-dom/vitest';
import { act, cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import AdjustmentEvidenceScreen, { exportAdjustmentEvidenceJson, stateForAdjustmentEvidence } from './AdjustmentEvidenceScreen';
import { adjustmentEvidenceFixture, blockedAdjustmentEvidenceFixture } from './fixtures';
import { adjustmentEvidenceScreenModule } from './index';

afterEach(() => cleanup());

describe('PII-24-S15 adjustment evidence screen', () => {
  it('defines the route module and evidence target', () => {
    expect(adjustmentEvidenceScreenModule.routePattern).toBe('/pricing/adjustments');
    expect(adjustmentEvidenceScreenModule.evidenceTarget).toBe('.local-harness/evidence/PII-24-S15/adjustment-evidence.json');
    expect(adjustmentEvidenceScreenModule.match('/pricing/adjustments')).toBe(true);
    expect(adjustmentEvidenceScreenModule.stateCoverage).toEqual(expect.arrayContaining(['load-state', 'blocked', 'ids', 'fact-refs', 'conflicts', 'compensation-hooks', 'summaries']));
  });

  it('fetches adjustment evidence with tenant context and story trace id', async () => {
    const fetchImpl = vi.fn(async () => ({ status: 200, json: async () => adjustmentEvidenceFixture })) as unknown as typeof fetch;
    render(<AdjustmentEvidenceScreen tenantContext="tenant-fixture" fetchImpl={fetchImpl} />);

    await act(async () => { await Promise.resolve(); });

    expect(fetchImpl).toHaveBeenCalledWith('/api/v1/adjustments/evidence?tenantContext=tenant-fixture', expect.objectContaining({ headers: expect.objectContaining({ 'X-Ui-Trace-Id': 'adjustment-s15-local-trace' }) }));
    expect(await screen.findByRole('heading', { name: 'Adjustment Evidence' })).toBeInTheDocument();
  });

  it('renders adjustments with fact refs, source version, summary, conflicts, and compensation hooks', () => {
    const onNavigate = vi.fn();
    const onEvidenceCapture = vi.fn();
    render(<AdjustmentEvidenceScreen evidence={adjustmentEvidenceFixture} onNavigate={onNavigate} onEvidenceCapture={onEvidenceCapture} />);

    const table = screen.getByRole('table', { name: /Adjustment evidence table/i });
    expect(within(table).getByText('adj-fixture-001')).toBeInTheDocument();
    expect(within(table).getByText('Credit and collateral adjustment bundle')).toBeInTheDocument();
    expect(screen.getAllByText('adjustment-config:v2026-06-fixture').length).toBeGreaterThan(0);
    expect(screen.getByText('hook:lo-compensation-ref-001')).toBeInTheDocument();
    expect(screen.getByText('conflict-fixture-001')).toBeInTheDocument();

    fireEvent.click(screen.getAllByRole('button', { name: /View in Waterfall/i })[0]);
    expect(onNavigate).toHaveBeenCalledWith('/quote/preview/pricing-waterfall?adjustment=adj-fixture-001');
    expect(onEvidenceCapture).toHaveBeenCalledWith(expect.objectContaining({ screenId: 'adjustment-evidence', evidenceTarget: '.local-harness/evidence/PII-24-S15/adjustment-evidence.json' }));
  });

  it('renders conflict ownership and assign affordance', () => {
    render(<AdjustmentEvidenceScreen evidence={adjustmentEvidenceFixture} />);
    fireEvent.click(screen.getByRole('button', { name: 'conflicts' }));

    expect(screen.getByRole('table', { name: /Adjustment conflicts/i })).toHaveTextContent('CONFLICTING FACT REFS');
    expect(screen.getByText('pricing ops')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Assign conflict-fixture-001 to me/i })).toBeInTheDocument();
  });

  it('renders compensation hooks and backend-owned formula refs without local calculations', () => {
    render(<AdjustmentEvidenceScreen evidence={adjustmentEvidenceFixture} />);
    fireEvent.click(screen.getByRole('button', { name: 'compensation' }));

    expect(screen.getByRole('heading', { name: /Compensation Hooks/i })).toBeInTheDocument();
    expect(screen.getByText(/does not calculate compensation/i)).toBeInTheDocument();
    expect(screen.getAllByText('hook:branch-compensation-ref-001').length).toBeGreaterThan(0);
  });

  it('exports summaries, audit refs, and replay hash', () => {
    render(<AdjustmentEvidenceScreen evidence={adjustmentEvidenceFixture} />);
    fireEvent.click(screen.getByRole('button', { name: 'summaries' }));
    expect(screen.getByText('summary:fico-ltv-fixture')).toBeInTheDocument();
    expect(screen.getByText('audit:adjustment-view-001')).toBeInTheDocument();
    expect(screen.getAllByText('replay-hash-adjustment-evidence-001').length).toBeGreaterThan(0);

    fireEvent.click(screen.getByRole('button', { name: /Export evidence JSON/i }));
    expect((screen.getByLabelText(/Exported adjustment evidence/i) as HTMLTextAreaElement).value).toContain('PII-24-S15');
    expect(exportAdjustmentEvidenceJson(adjustmentEvidenceFixture)).toContain('adjustment-evidence');
  });

  it('renders blocked and empty states without synthesized rows', () => {
    render(<AdjustmentEvidenceScreen evidence={blockedAdjustmentEvidenceFixture} />);

    expect(screen.getByRole('alert')).toHaveTextContent(/Adjustment evidence API is unavailable/i);
    expect(screen.getByText('No adjustment rows match the current filters.')).toBeInTheDocument();
    expect(stateForAdjustmentEvidence(blockedAdjustmentEvidenceFixture)).toBe('blocked');
  });
});
