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
    expect(screen.getByText('Selected by backend rank service.')).toBeInTheDocument();
    expect(screen.getByText('ATR_QM')).toBeInTheDocument();
    expect(screen.getByText('replay-hash-offer-a')).toBeInTheDocument();
    expect(onEvidenceCapture).toHaveBeenCalledWith(expect.objectContaining({ screenId: 'quote-detail', state: 'needs-attention' }));
  });

  it('renders the waterfall ledger with table semantics and redaction metadata', () => {
    render(<QuoteDetailScreen detail={deterministicQuoteDetail} />);

    const ledger = screen.getByRole('table', { name: /Pricing waterfall ledger/i });
    expect(within(ledger).getByText('Compensation adjustment')).toBeInTheDocument();
    expect(within(ledger).getAllByText('[REDACTED]').length).toBeGreaterThanOrEqual(2);
    expect(screen.getAllByText(/COMPENSATION_CONFIDENTIAL/).length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('audit:redaction-001').length).toBeGreaterThanOrEqual(1);
  });

  it('exports backend waterfall data and navigates compliance and audit actions', () => {
    const onNavigate = vi.fn();
    render(<QuoteDetailScreen detail={deterministicQuoteDetail} onNavigate={onNavigate} />);

    fireEvent.click(screen.getByRole('button', { name: /Export Waterfall/i }));
    expect((screen.getByLabelText(/Exported waterfall data/i) as HTMLTextAreaElement).value).toContain('BACKEND_ADJUSTMENT');

    fireEvent.click(screen.getAllByRole('button', { name: /Open compliance evidence/i })[0]);
    fireEvent.click(screen.getByRole('button', { name: /Open audit replay/i }));
    expect(onNavigate).toHaveBeenCalledWith('/compliance/evidence/ATR_QM/audit:offer-a');
    expect(onNavigate).toHaveBeenCalledWith('/audit/replay?ref=replay-hash-offer-a');
  });
});
