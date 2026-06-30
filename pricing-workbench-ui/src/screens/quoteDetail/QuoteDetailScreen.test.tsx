import '@testing-library/jest-dom/vitest';
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import QuoteDetailScreen from './QuoteDetailScreen';
import { deterministicQuoteDetail } from './fixtures';
import { quoteDetailScreenModule } from './index';

afterEach(() => cleanup());

describe('PII-24-S11 quote detail waterfall screen', () => {
  it('defines the quote detail route module and evidence target', () => {
    expect(quoteDetailScreenModule.routePattern).toBe('/quote/:runId/offers/:optionId');
    expect(quoteDetailScreenModule.evidenceTarget).toBe('.local-harness/evidence/PII-24-S11/quote-detail.json');
    expect(quoteDetailScreenModule.match('/quote/run-preview-001/offers/offer-a')).toBe(true);
  });

  it('renders summary, explanation, compliance, audit, and replay evidence from fixture data', () => {
    const onEvidenceCapture = vi.fn();
    render(<QuoteDetailScreen detail={deterministicQuoteDetail} onEvidenceCapture={onEvidenceCapture} />);

    expect(screen.getByRole('heading', { name: /Quote Detail Waterfall/i })).toBeInTheDocument();
    expect(screen.getAllByText('Conventional 30 year fixed').length).toBeGreaterThanOrEqual(1);
    expect(screen.queryByText('Selected by backend rank service.')).not.toBeInTheDocument();
    expect(screen.getByText('Pricing evidence is rendered from the returned waterfall ledger.')).toBeInTheDocument();
    expect(screen.getByText('rule:loanpass:max-ltv-config-ref')).toBeInTheDocument();
    expect(screen.getByText('stipulation:income-documentation-required')).toBeInTheDocument();
    expect(screen.getByText('rate:note-rate:6.500')).toBeInTheDocument();
    expect(screen.getByText('lock-period:30')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('tab', { name: /Compliance/i }));
    expect(screen.getByText('ATR_QM')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('tab', { name: /Audit/i }));
    expect(screen.getByText('replay-hash-offer-a')).toBeInTheDocument();
    expect(onEvidenceCapture).toHaveBeenCalledWith(expect.objectContaining({ screenId: 'quote-detail', state: 'needs-attention' }));
  }, 10000);

  it('renders the waterfall ledger with table semantics and redaction metadata', () => {
    render(<QuoteDetailScreen detail={deterministicQuoteDetail} />);

    expect(screen.queryByRole('table', { name: /Pricing waterfall ledger/i })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('tab', { name: /Waterfall/i }));

    const ledger = screen.getByRole('table', { name: /Pricing waterfall ledger/i });
    expect(within(ledger).getByText('Compensation adjustment')).toBeInTheDocument();
    expect(within(ledger).getAllByText('[REDACTED]').length).toBeGreaterThanOrEqual(2);
    expect(screen.getAllByText(/COMPENSATION_CONFIDENTIAL/).length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('audit:redaction-001').length).toBeGreaterThanOrEqual(1);
  });

  it('exports backend waterfall data and navigates compliance and audit actions', () => {
    const onNavigate = vi.fn();
    render(<QuoteDetailScreen detail={deterministicQuoteDetail} onNavigate={onNavigate} />);

    fireEvent.click(screen.getByRole('tab', { name: /Waterfall/i }));
    fireEvent.click(screen.getByRole('button', { name: /Export Waterfall/i }));
    expect((screen.getByLabelText(/Exported waterfall data/i) as HTMLTextAreaElement).value).toContain('BACKEND_ADJUSTMENT');

    fireEvent.click(screen.getByRole('tab', { name: /Compliance/i }));
    fireEvent.click(screen.getAllByRole('button', { name: /Open compliance evidence/i })[0]);
    fireEvent.click(screen.getByRole('tab', { name: /Audit/i }));
    fireEvent.click(screen.getByRole('button', { name: /Open audit replay/i }));
    expect(onNavigate).toHaveBeenCalledWith('/compliance/evidence/ATR_QM/audit:offer-a');
    expect(onNavigate).toHaveBeenCalledWith('/audit/replay?ref=replay-hash-offer-a');
  });

  it('does not use fixture pricing as a runtime default when no detail is supplied', () => {
    const onEvidenceCapture = vi.fn();
    render(<QuoteDetailScreen onEvidenceCapture={onEvidenceCapture} />);

    expect(screen.getByRole('heading', { name: /Quote Detail Waterfall/i })).toBeInTheDocument();
    expect(screen.queryByText('Conventional 30 year fixed')).not.toBeInTheDocument();
    expect(screen.queryByText('6.500%')).not.toBeInTheDocument();
    expect(screen.getByText(/quote detail evidence is not loaded|select a quote option/i)).toBeInTheDocument();
    expect(onEvidenceCapture).toHaveBeenCalledWith(expect.objectContaining({ screenId: 'quote-detail', state: 'empty' }));
  });

  it('renders loading and error states for backend quote detail fetches', async () => {
    const pendingFetch = vi.fn(() => new Promise<Response>(() => undefined)) as unknown as typeof fetch;
    const { unmount } = render(<QuoteDetailScreen tenantId="tenant-a" runId="run-a" optionId="offer-a" fetchImpl={pendingFetch} />);

    expect(await screen.findByRole('status')).toHaveTextContent(/Loading quote detail evidence/i);
    expect(pendingFetch).toHaveBeenCalledWith('/api/v1/tenants/tenant-a/quote-runs/run-a/offers/offer-a/detail', expect.any(Object));
    unmount();

    const failingFetch = vi.fn(async () => { throw new Error('detail API unavailable'); }) as unknown as typeof fetch;
    render(<QuoteDetailScreen tenantId="tenant-a" runId="run-a" optionId="offer-a" fetchImpl={failingFetch} />);

    expect(await screen.findByRole('alert')).toHaveTextContent('detail API unavailable');
  });

  it('exposes only the selected tab panel', () => {
    render(<QuoteDetailScreen detail={deterministicQuoteDetail} />);

    expect(screen.getByRole('tabpanel', { name: /Summary/i })).toBeVisible();
    expect(screen.queryByRole('table', { name: /Pricing waterfall ledger/i })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('tab', { name: /Waterfall/i }));
    expect(screen.getByRole('tabpanel', { name: /Waterfall/i })).toBeVisible();
    expect(screen.getByRole('table', { name: /Pricing waterfall ledger/i })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Open audit replay/i })).not.toBeInTheDocument();
  });
});
