import '@testing-library/jest-dom/vitest';
import { act, cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import PricingWaterfallScreen, { exportWaterfallCsv, exportWaterfallJson } from './PricingWaterfallScreen';
import { blockedPricingWaterfall, deterministicPricingWaterfall } from './fixtures';
import { pricingWaterfallScreenModule } from './index';

afterEach(() => cleanup());

describe('PII-24-S14 pricing waterfall screen', () => {
  it('defines the route module and evidence target', () => {
    expect(pricingWaterfallScreenModule.routePattern).toBe('/quote/:runId/pricing-waterfall');
    expect(pricingWaterfallScreenModule.evidenceTarget).toBe('.local-harness/evidence/PII-24-S14/pricing-waterfall.json');
    expect(pricingWaterfallScreenModule.match('/quote/run-preview-001/pricing-waterfall')).toBe(true);
    expect(pricingWaterfallScreenModule.stateCoverage).toEqual(expect.arrayContaining(['load-state', 'blocked', 'redacted', 'ready', 'replay-evidence']));
  });

  it('fetches the API-shaped waterfall without local pricing calculation', async () => {
    const fetchImpl = vi.fn(async () => ({ status: 200, json: async () => deterministicPricingWaterfall })) as unknown as typeof fetch;
    render(<PricingWaterfallScreen runId="run-preview-001" fetchImpl={fetchImpl} />);

    await act(async () => { await Promise.resolve(); });
    expect(fetchImpl).toHaveBeenCalledWith('/api/v1/tenants/tenant-fixture/quote-runs/run-preview-001/pricing-waterfall', expect.objectContaining({ headers: expect.objectContaining({ Accept: 'application/json' }) }));
    expect(await screen.findByRole('heading', { name: /Pricing Waterfall/i })).toBeInTheDocument();
    expect(screen.getByText(/without local pricing calculation/i)).toBeInTheDocument();
  });

  it('renders base selection, final price, blockers, evidence refs, and redacted cells', () => {
    const onEvidenceCapture = vi.fn();
    render(<PricingWaterfallScreen waterfall={deterministicPricingWaterfall} onEvidenceCapture={onEvidenceCapture} />);

    expect(screen.getByText('grid:v2026-06-waterfall-fixture')).toBeInTheDocument();
    expect(screen.getByText('100.875')).toBeInTheDocument();
    expect(screen.getByText('No blockers returned.')).toBeInTheDocument();
    expect(screen.getByText('replay-hash-waterfall-001')).toBeInTheDocument();
    expect(screen.getAllByText('[REDACTED]').length).toBeGreaterThanOrEqual(2);
    expect(screen.getAllByText(/MARGIN_CONFIDENTIAL/).length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('audit:redaction-001').length).toBeGreaterThanOrEqual(1);
    expect(onEvidenceCapture).toHaveBeenCalledWith(expect.objectContaining({ screenId: 'pricing-waterfall', state: 'needs-attention' }));
  });

  it('groups, filters, and expands ledger rows without expanded details by default', () => {
    render(<PricingWaterfallScreen waterfall={deterministicPricingWaterfall} />);

    const ledger = screen.getByRole('table', { name: /Pricing waterfall ledger/i });
    expect(within(ledger).getByText('Margins step 7')).toBeInTheDocument();
    expect(screen.queryByText('Backend input ref 7')).not.toBeInTheDocument();

    fireEvent.change(screen.getByLabelText(/Reason code/i), { target: { value: 'REDACTED_MARGIN' } });
    expect(within(ledger).getByText('Margins step 7')).toBeInTheDocument();
    expect(within(ledger).queryByText('Base Rate step 1')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '7' }));
    expect(screen.getByText('Backend input ref 7')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /Collapse All/i }));
    expect(screen.queryByText('Backend input ref 7')).not.toBeInTheDocument();
  });

  it('exports CSV and JSON with redaction metadata', () => {
    expect(exportWaterfallCsv(deterministicPricingWaterfall)).toContain('inputAuditRef');
    expect(exportWaterfallCsv(deterministicPricingWaterfall)).toContain('audit:redaction-001');
    expect(exportWaterfallJson(deterministicPricingWaterfall)).toContain('"redactions"');

    render(<PricingWaterfallScreen waterfall={deterministicPricingWaterfall} />);
    fireEvent.click(screen.getByRole('button', { name: /Export CSV/i }));
    expect((screen.getByLabelText(/Exported pricing waterfall/i) as HTMLTextAreaElement).value).toContain('REDACTED_MARGIN');
    fireEvent.click(screen.getByRole('button', { name: /Export JSON/i }));
    expect((screen.getByLabelText(/Exported pricing waterfall/i) as HTMLTextAreaElement).value).toContain('"redactions"');
    expect(screen.getByRole('button', { name: /Export PDF unavailable/i })).toBeDisabled();
  });

  it('renders blocked waterfall remediation and source refs', () => {
    const onNavigate = vi.fn();
    render(<PricingWaterfallScreen waterfall={blockedPricingWaterfall} onNavigate={onNavigate} />);

    expect(screen.getByRole('alert')).toHaveTextContent(/blocked/i);
    expect(screen.getByText('WATERFALL_SOURCE_UNAVAILABLE')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /pricing-service:waterfall/i }));
    expect(onNavigate).toHaveBeenCalledWith('/governance/config?ref=pricing-service%3Awaterfall');
  });
});
