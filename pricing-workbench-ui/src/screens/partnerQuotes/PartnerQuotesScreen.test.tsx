import '@testing-library/jest-dom/vitest';
import { act, cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import PartnerQuotesLayout, { stateForPartnerQuotes } from './PartnerQuotesLayout';
import { blockedPartnerQuotesFixture, partnerQuoteDetailFixture, partnerQuotesFixture } from './fixtures';
import { partnerQuotesScreenModule } from './index';

afterEach(() => cleanup());

describe('PII-24-S21 partner quotes screen', () => {
  it('defines the /partners/quotes route module and PII-24-S21 evidence target', () => {
    expect(partnerQuotesScreenModule.routePattern).toBe('/partners/quotes');
    expect(partnerQuotesScreenModule.evidenceTarget).toBe('.local-harness/evidence/PII-24-S21/partner-quotes.json');
    expect(partnerQuotesScreenModule.match('/partners/quotes')).toBe(true);
    expect(partnerQuotesScreenModule.stateCoverage).toEqual(expect.arrayContaining(['load-state', 'empty', 'blocked', 'ready']));
  });

  it('fetches partner quote list with story-safe refs and renders the workspace', async () => {
    const fetchImpl = vi.fn(async (input: RequestInfo | URL) => ({ status: 200, json: async () => input.toString().includes('/pq-fixture-001') ? partnerQuoteDetailFixture : partnerQuotesFixture })) as unknown as typeof fetch;
    render(<PartnerQuotesLayout tenantContext="tenant-fixture" partnerId="partner-fixture" fetchImpl={fetchImpl} />);

    await act(async () => { await Promise.resolve(); });

    expect(fetchImpl).toHaveBeenCalledWith('/api/v1/partners/partner-fixture/quotes', expect.objectContaining({ headers: expect.objectContaining({ 'X-Ui-Trace-Id': 'ch-s02-local-trace' }) }));
    expect(await screen.findByRole('heading', { name: 'Partner Quotes' })).toBeInTheDocument();
  });

  it('renders filters, sorting, row fields, detail side panel, lifecycle timeline, SLA refs, and error flags', () => {
    const onEvidenceCapture = vi.fn();
    render(<PartnerQuotesLayout evidence={partnerQuotesFixture} detailEvidence={partnerQuoteDetailFixture} onEvidenceCapture={onEvidenceCapture} />);

    const table = screen.getByRole('table', { name: 'Partner quotes list' });
    expect(within(table).getByText('pq-fixture-001')).toBeInTheDocument();
    expect(within(table).getByText('Borrower fixture A')).toBeInTheDocument();
    expect(within(table).getByText('ON_TRACK')).toBeInTheDocument();
    expect(within(table).getByText('UNLOCKED')).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('Partner filter'), { target: { value: 'correspondent-fixture' } });
    expect(within(table).queryByText('pq-fixture-001')).not.toBeInTheDocument();
    expect(within(table).getByText('pq-fixture-002')).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Partner filter'), { target: { value: 'all' } });
    fireEvent.change(screen.getByLabelText('Sort'), { target: { value: 'borrower' } });
    fireEvent.click(screen.getAllByRole('button', { name: 'Open quote detail' })[0]);

    expect(screen.getByRole('heading', { name: 'Partner Quote Detail' })).toBeInTheDocument();
    expect(screen.getByRole('table', { name: 'Partner quote lifecycle timeline' })).toHaveTextContent('SUBMITTED');
    expect(screen.getByText('sla-target-ref:pq-fixture-001')).toBeInTheDocument();
    expect(screen.getByText('No error flags supplied for this quote.')).toBeInTheDocument();
    expect(onEvidenceCapture).toHaveBeenCalledWith(expect.objectContaining({ screenId: 'partner-quotes', evidenceTarget: '.local-harness/evidence/PII-24-S21/partner-quotes.json' }));
  });

  it('opens reprice modal and calls the configured API without local pricing calculations', async () => {
    const fetchImpl = vi.fn(async () => ({ status: 202, json: async () => ({ quoteId: 'pq-fixture-001', status: 'ACCEPTED', message: 'Reprice request accepted by configured API.', guidance: 'Pricing-service owns recalculation.', supportHandoffRoute: '/partners/support/reprice', uiTraceId: 'partner-quotes-s21-local-trace', events: ['PartnerRepriceRequested'] }) })) as unknown as typeof fetch;
    render(<PartnerQuotesLayout evidence={partnerQuotesFixture} detailEvidence={partnerQuoteDetailFixture} fetchImpl={fetchImpl} />);

    fireEvent.click(screen.getByRole('button', { name: 'Reprice' }));
    expect(screen.getByRole('dialog', { name: 'Request Reprice' })).toHaveTextContent('pricing calculations remain service-owned');
    fireEvent.click(screen.getByRole('button', { name: 'Request Reprice' }));
    await act(async () => { await Promise.resolve(); });

    expect(fetchImpl).toHaveBeenCalledWith('/api/v1/partners/partner-fixture/quotes/pq-fixture-001/reprice', expect.objectContaining({ method: 'POST' }));
    expect(screen.getByText('Reprice request accepted by configured API.')).toBeInTheDocument();
  });

  it('renders empty and blocked states without synthesized quote rows', () => {
    render(<PartnerQuotesLayout evidence={blockedPartnerQuotesFixture} />);

    expect(screen.getByRole('alert')).toHaveTextContent('BLOCKED_PARTNER_QUOTES_CONTRACT_REQUIRED');
    expect(screen.getByText('No partner quotes match the selected filters.')).toBeInTheDocument();
    expect(stateForPartnerQuotes(blockedPartnerQuotesFixture)).toBe('blocked');
  });
});
