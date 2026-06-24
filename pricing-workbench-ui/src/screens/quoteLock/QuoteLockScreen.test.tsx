import '@testing-library/jest-dom/vitest';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import QuoteLockScreen, { countdownWarning } from './LockWorkflow';
import { blockedLockWorkflow, deterministicLockConfirmation, deterministicLockWorkflow } from './fixtures';
import { quoteLockScreenModule } from './index';
import { historyCsv } from './LockHistory';

afterEach(() => {
  vi.useRealTimers();
  cleanup();
});

describe('PII-24-S12 quote lock workflow screen', () => {
  it('defines the quote lock route module and evidence target', () => {
    expect(quoteLockScreenModule.routePattern).toBe('/quote/:runId/lock');
    expect(quoteLockScreenModule.evidenceTarget).toBe('.local-harness/evidence/PII-24-S12/quote-lock.json');
    expect(quoteLockScreenModule.match('/quote/run-preview-001/lock')).toBe(true);
    expect(quoteLockScreenModule.stateCoverage).toEqual(expect.arrayContaining(['load-state', 'blocked', 'ready', 'confirmed', 'expired', 'extended', 'relocked', 'float-down']));
  });

  it('fetches lock workflow on mount and polls every 30s while confirmed', async () => {
    vi.useFakeTimers();
    const fetchImpl = vi.fn(async () => ({ status: 200, json: async () => ({ ...deterministicLockWorkflow, status: 'CONFIRMED', lockId: 'lock-confirmed-offer-a' }) })) as unknown as typeof fetch;

    render(<QuoteLockScreen runId="run-preview-001" optionId="offer-a" fetchImpl={fetchImpl} />);

    await act(async () => { await Promise.resolve(); });
    expect(fetchImpl).toHaveBeenCalledWith('/api/v1/tenants/tenant-fixture/quote-runs/run-preview-001/lock?selectedOfferId=offer-a', expect.objectContaining({ headers: expect.objectContaining({ Accept: 'application/json' }) }));
    const callCountAfterMount = vi.mocked(fetchImpl).mock.calls.length;
    await act(async () => { await vi.advanceTimersByTimeAsync(30000); });
    expect(vi.mocked(fetchImpl).mock.calls.length).toBeGreaterThan(callCountAfterMount);
  });

  it('renders backend-shaped lock terms, status, post-lock actions, and history from fixture data', () => {
    const onEvidenceCapture = vi.fn();
    render(<QuoteLockScreen workflow={deterministicLockWorkflow} onEvidenceCapture={onEvidenceCapture} />);

    expect(screen.getByRole('heading', { name: /Lock Workflow/i })).toBeInTheDocument();
    expect(screen.getByText('Conventional 30 year fixed')).toBeInTheDocument();
    expect(screen.getByText('Backend Investor A')).toBeInTheDocument();
    expect(screen.getByRole('status', { name: /Lock status banner/i })).toHaveTextContent(/READY/);
    expect(screen.getByRole('button', { name: /Extend Lock/i })).toBeInTheDocument();
    expect(screen.getByText('audit:lock-created')).toBeInTheDocument();
    expect(onEvidenceCapture).toHaveBeenCalledWith(expect.objectContaining({ screenId: 'quote-lock', state: 'ready' }));
  });

  it('requires disclosure scroll completion, checkbox, and typed signature before confirmation', async () => {
    const confirmLock = vi.fn(() => deterministicLockConfirmation);
    render(<QuoteLockScreen workflow={deterministicLockWorkflow} confirmLock={confirmLock} />);

    expect(screen.getByRole('button', { name: /Lock This Rate/i })).toBeDisabled();

    fireEvent.scroll(screen.getByLabelText(/Disclosure text/i), { currentTarget: { scrollTop: 100, clientHeight: 100, scrollHeight: 200 } });
    fireEvent.click(screen.getByLabelText(/I have read and accept/i));
    fireEvent.change(screen.getByLabelText(/Digital signature/i), { target: { value: 'Ada Borrower' } });
    fireEvent.click(screen.getByRole('button', { name: /Lock This Rate/i }));
    fireEvent.click(screen.getByRole('button', { name: /^Confirm Lock$/i }));

    expect(confirmLock).toHaveBeenCalledWith(expect.objectContaining({ disclosuresAccepted: true, disclosureScrollComplete: true, signatureName: 'Ada Borrower' }));
    await waitFor(() => expect(screen.getByRole('status', { name: /Lock status banner/i })).toHaveTextContent(/CONFIRMED/));
  });

  it('uses the default typed confirm API for confirm and post-lock actions', async () => {
    const fetchImpl = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      if (String(input).endsWith('/lock/confirm')) {
        return { status: 200, json: async () => deterministicLockConfirmation };
      }

      return { status: 200, json: async () => deterministicLockWorkflow };
    }) as unknown as typeof fetch;
    render(<QuoteLockScreen workflow={deterministicLockWorkflow} fetchImpl={fetchImpl} />);

    fireEvent.scroll(screen.getByLabelText(/Disclosure text/i), { currentTarget: { scrollTop: 100, clientHeight: 100, scrollHeight: 200 } });
    fireEvent.click(screen.getByLabelText(/I have read and accept/i));
    fireEvent.change(screen.getByLabelText(/Digital signature/i), { target: { value: 'Ada Borrower' } });
    fireEvent.click(screen.getByRole('button', { name: /Lock This Rate/i }));
    fireEvent.click(screen.getByRole('button', { name: /^Confirm Lock$/i }));
    await waitFor(() => expect(fetchImpl).toHaveBeenCalledWith('/api/v1/tenants/tenant-fixture/quote-runs/run-preview-001/lock/confirm', expect.objectContaining({ method: 'POST' })));

    fireEvent.click(screen.getByRole('button', { name: /Extend Lock/i }));
    fireEvent.click(screen.getByRole('button', { name: /Confirm Extend Lock/i }));
    await waitFor(() => expect(JSON.parse(String(vi.mocked(fetchImpl).mock.calls.at(-1)?.[1]?.body))).toEqual(expect.objectContaining({ action: 'extend' })));
  });

  it('shows blocked or unavailable when lock-service action fails without changing current status', async () => {
    const confirmLock = vi.fn(async () => {
      throw new Error('lock-service unavailable');
    });
    render(<QuoteLockScreen workflow={deterministicLockWorkflow} confirmLock={confirmLock} />);

    fireEvent.scroll(screen.getByLabelText(/Disclosure text/i), { currentTarget: { scrollTop: 100, clientHeight: 100, scrollHeight: 200 } });
    fireEvent.click(screen.getByLabelText(/I have read and accept/i));
    fireEvent.change(screen.getByLabelText(/Digital signature/i), { target: { value: 'Ada Borrower' } });
    fireEvent.click(screen.getByRole('button', { name: /Lock This Rate/i }));
    fireEvent.click(screen.getByRole('button', { name: /^Confirm Lock$/i }));

    const unavailableAlert = await screen.findByText(/Lock-service lock confirmation is blocked or unavailable/i);
    expect(unavailableAlert).toHaveTextContent(/Current lock status remains READY/i);
    expect(screen.getByRole('status', { name: /Lock status banner/i })).toHaveTextContent(/READY/);
    expect(screen.queryByText(/Local synthetic\/dev fixture staged/i)).not.toBeInTheDocument();
    expect(confirmLock).toHaveBeenCalledWith(expect.objectContaining({ action: 'confirm' }));
  });

  it('derives countdown warning labels from expiration timestamps', () => {
    const now = new Date('2026-06-11T18:00:00Z');
    expect(countdownWarning('2026-06-14T18:00:00Z', now).severity).toBe('info');
    expect(countdownWarning('2026-06-12T17:00:00Z', now).severity).toBe('warning');
    expect(countdownWarning('2026-06-11T19:30:00Z', now).severity).toBe('critical');
    expect(countdownWarning('2026-06-11T17:59:00Z', now).severity).toBe('expired');
  });

  it('renders blocked remediation and return-to-offers action', () => {
    const onNavigate = vi.fn();
    render(<QuoteLockScreen workflow={blockedLockWorkflow} onNavigate={onNavigate} />);

    expect(screen.getByRole('heading', { name: /Lock workflow blocked/i })).toBeInTheDocument();
    expect(screen.getByText('DISCLOSURE_MISSING')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /Return to Offers/i }));
    expect(onNavigate).toHaveBeenCalledWith('/quote/run-preview-001/offers');
  });

  it('exports lock history as CSV', () => {
    expect(historyCsv(deterministicLockWorkflow.history)).toContain('"eventId","eventType","timestamp","actor","terms","approvalRef","auditRef"');
    expect(historyCsv(deterministicLockWorkflow.history)).toContain('"evt-created","created"');
  });
});
