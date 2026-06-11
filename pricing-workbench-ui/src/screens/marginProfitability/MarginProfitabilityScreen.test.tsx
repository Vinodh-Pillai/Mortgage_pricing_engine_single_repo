import '@testing-library/jest-dom/vitest';
import { act, cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import MarginProfitabilityScreen, { exportMarginProfitabilityJson, stateForMarginProfitability } from './MarginProfitabilityScreen';
import { blockedMarginProfitabilityFixture, marginProfitabilityFixture } from './fixtures';
import { marginProfitabilityScreenModule } from './index';

afterEach(() => cleanup());

describe('PII-24-S16 margin profitability screen', () => {
  it('defines the route module and evidence target', () => {
    expect(marginProfitabilityScreenModule.routePattern).toBe('/pricing/margins');
    expect(marginProfitabilityScreenModule.evidenceTarget).toBe('.local-harness/evidence/PII-24-S16/margin-profitability.json');
    expect(marginProfitabilityScreenModule.match('/pricing/margins')).toBe(true);
    expect(marginProfitabilityScreenModule.stateCoverage).toEqual(expect.arrayContaining(['load-state', 'blocked', 'redacted', 'floor-evidence', 'approval', 'replay-evidence']));
  });

  it('fetches margin profitability with tenant context and story trace id', async () => {
    const fetchImpl = vi.fn(async () => ({ status: 200, json: async () => marginProfitabilityFixture })) as unknown as typeof fetch;
    render(<MarginProfitabilityScreen tenantContext="tenant-fixture" fetchImpl={fetchImpl} />);

    await act(async () => { await Promise.resolve(); });

    expect(fetchImpl).toHaveBeenCalledWith('/api/v1/margins/profitability?tenantContext=tenant-fixture', expect.objectContaining({ headers: expect.objectContaining({ 'X-Ui-Trace-Id': 'margin-s16-local-trace' }) }));
    expect(await screen.findByRole('heading', { name: 'Margin Profitability' })).toBeInTheDocument();
  });

  it('renders company, branch, LO, investor, and secondary market margin sections with redactions and access request', () => {
    const onEvidenceCapture = vi.fn();
    render(<MarginProfitabilityScreen evidence={marginProfitabilityFixture} onEvidenceCapture={onEvidenceCapture} />);

    const sectionList = screen.getByRole('list', { name: /Margin sections/i });
    expect(within(sectionList).getByText('Company Margin')).toBeInTheDocument();
    expect(within(sectionList).getByText('Branch Margin')).toBeInTheDocument();
    expect(within(sectionList).getByText('LO Margin')).toBeInTheDocument();
    expect(within(sectionList).getByText('Investor Margin')).toBeInTheDocument();
    expect(within(sectionList).getByText('Secondary Market Margin')).toBeInTheDocument();
    expect(screen.getAllByText('[REDACTED]').length).toBeGreaterThan(0);
    expect(screen.getByRole('button', { name: /Request access to LO Margin/i })).toBeInTheDocument();
    expect(onEvidenceCapture).toHaveBeenCalledWith(expect.objectContaining({ screenId: 'margin-profitability', evidenceTarget: '.local-harness/evidence/PII-24-S16/margin-profitability.json' }));
  });

  it('renders floor evidence decisions, policy refs, audit refs, and exception navigation', () => {
    const onNavigate = vi.fn();
    render(<MarginProfitabilityScreen evidence={marginProfitabilityFixture} onNavigate={onNavigate} />);

    const table = screen.getByRole('table', { name: /Floor evidence table/i });
    expect(within(table).getByText('PASS')).toBeInTheDocument();
    expect(within(table).getByText('FAIL')).toBeInTheDocument();
    expect(within(table).getByText('WARN')).toBeInTheDocument();
    expect(screen.getAllByText('floor-policy:v-fixture').length).toBeGreaterThan(0);
    expect(screen.getByText('audit:floor-fail-001')).toBeInTheDocument();

    fireEvent.click(screen.getAllByRole('button', { name: /View Exception/i })[1]);
    expect(onNavigate).toHaveBeenCalledWith('/exceptions/concessions?quoteOptionId=quote-option-fixture-fail');
  });

  it('renders approval chain, role-gated actions, snapshots, and audit refs', () => {
    render(<MarginProfitabilityScreen evidence={marginProfitabilityFixture} />);

    expect(screen.getByRole('heading', { name: /Version, chain, actions, snapshots, and audit refs/i })).toBeInTheDocument();
    expect(screen.getByRole('table', { name: /Approval chain/i })).toHaveTextContent('pricing manager');
    expect(screen.getByRole('button', { name: 'Request Approval' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Approve' })).toBeEnabled();
    expect(screen.getByRole('button', { name: 'Reject' })).toBeEnabled();
    expect(screen.getByText('snapshot:margin-approval-001')).toBeInTheDocument();
  });

  it('exports version refs, audit refs, replay hash, and bounded blocked states', () => {
    render(<MarginProfitabilityScreen evidence={marginProfitabilityFixture} />);
    expect(screen.getByText('replay-hash-margin-profitability-001')).toBeInTheDocument();
    expect(screen.getByText('governance-config:margin-fixture')).toBeInTheDocument();
    expect(screen.getByText('audit:margin-view-001')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /Export evidence JSON/i }));
    expect((screen.getByLabelText(/Exported margin profitability evidence/i) as HTMLTextAreaElement).value).toContain('PII-24-S16');
    expect(exportMarginProfitabilityJson(marginProfitabilityFixture)).toContain('margin-profitability');
    expect(stateForMarginProfitability(blockedMarginProfitabilityFixture)).toBe('blocked');
  });
});
