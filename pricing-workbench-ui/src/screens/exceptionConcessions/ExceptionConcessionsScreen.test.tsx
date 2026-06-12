import '@testing-library/jest-dom/vitest';
import { act, cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import ExceptionConcessionsScreen, { exportExceptionConcessionsJson, stateForExceptionConcessions } from './ExceptionConcessionsScreen';
import { blockedExceptionConcessionsFixture, exceptionConcessionsFixture } from './fixtures';
import { exceptionConcessionsScreenModule } from './index';

afterEach(() => cleanup());

describe('PII-24-S17 exception concessions screen', () => {
  it('defines the route module and PII-24-S17 evidence target', () => {
    expect(exceptionConcessionsScreenModule.routePattern).toBe('/exceptions/concessions');
    expect(exceptionConcessionsScreenModule.evidenceTarget).toBe('.local-harness/evidence/PII-24-S17/exception-concessions.json');
    expect(exceptionConcessionsScreenModule.match('/exceptions/concessions')).toBe(true);
    expect(exceptionConcessionsScreenModule.stateCoverage).toEqual(expect.arrayContaining(['load-state', 'concession-request', 'eligibility-exception', 'authority-matrix', 'manual-price-guard', 'risk-events', 'history-replay-export']));
  });

  it('fetches the workbench with tenant context and story trace id', async () => {
    const fetchImpl = vi.fn(async () => ({ status: 200, json: async () => exceptionConcessionsFixture })) as unknown as typeof fetch;
    render(<ExceptionConcessionsScreen tenantContext="tenant-fixture" fetchImpl={fetchImpl} />);

    await act(async () => { await Promise.resolve(); });

    expect(fetchImpl).toHaveBeenCalledWith('/api/v1/exceptions/concessions/workbench?tenantContext=tenant-fixture', expect.objectContaining({ headers: expect.objectContaining({ 'X-Ui-Trace-Id': 'exception-s17-local-trace' }) }));
    expect(await screen.findByRole('heading', { name: 'Exception Concessions' })).toBeInTheDocument();
  });

  it('renders concession requests with disabled mutation affordances and audit refs', () => {
    const onEvidenceCapture = vi.fn();
    render(<ExceptionConcessionsScreen evidence={exceptionConcessionsFixture} onEvidenceCapture={onEvidenceCapture} />);

    const table = screen.getByRole('table', { name: /Concession requests table/i });
    expect(within(table).getByText('concession-req-fixture-001')).toBeInTheDocument();
    expect(within(table).getByText('impact-ref:backend-owned-001')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Approve' })).toBeDisabled();
    expect(screen.getAllByText('audit:concession-request-fixture').length).toBeGreaterThan(0);
    expect(onEvidenceCapture).toHaveBeenCalledWith(expect.objectContaining({ screenId: 'exception-concessions', evidenceTarget: '.local-harness/evidence/PII-24-S17/exception-concessions.json' }));
  });

  it('shows eligibility and authority refs without local limit rules', () => {
    render(<ExceptionConcessionsScreen evidence={exceptionConcessionsFixture} />);

    fireEvent.click(screen.getByRole('button', { name: 'Eligibility Exceptions' }));
    expect(screen.getByRole('table', { name: /Eligibility exceptions table/i })).toHaveTextContent('rule-ref:eligibility-minimum-config');
    expect(screen.getByText('standard-value-ref:configured-rule')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Authority Matrix' }));
    expect(screen.getByRole('table', { name: /Authority matrix table/i })).toHaveTextContent('limit-ref:governance-config-owned');
    expect(screen.getByText(/no browser authority matrix logic/i)).toBeInTheDocument();
  });

  it('shows manual price guard and risk event backend refs', () => {
    render(<ExceptionConcessionsScreen evidence={exceptionConcessionsFixture} />);

    fireEvent.click(screen.getByRole('button', { name: 'Price Guard' }));
    expect(screen.getByRole('alert')).toHaveTextContent('BLOCKED BY EXCEPTION POLICY REF');
    expect(screen.getByText('replay-hash-price-guard-fixture')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Commit manual price mutation/i })).toBeDisabled();

    fireEvent.click(screen.getByRole('button', { name: 'Risk Events' }));
    expect(screen.getByRole('table', { name: /Risk events timeline/i })).toHaveTextContent('risk-event-fixture-001');
    expect(screen.getByText(/does not evaluate thresholds/i)).toBeInTheDocument();
  });

  it('exports history with audit refs and replay hashes', () => {
    render(<ExceptionConcessionsScreen evidence={exceptionConcessionsFixture} />);
    fireEvent.click(screen.getByRole('button', { name: 'History' }));

    expect(screen.getByText('replay-hash-history-fixture')).toBeInTheDocument();
    expect(screen.getByText('legal-hold-ref:configured-state')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /Export evidence JSON/i }));
    expect((screen.getByLabelText(/Exported exception concessions evidence/i) as HTMLTextAreaElement).value).toContain('PII-24-S17');
    expect(exportExceptionConcessionsJson(exceptionConcessionsFixture)).toContain('exception-concessions');
  });

  it('renders blocked state without synthesized exception rows', () => {
    render(<ExceptionConcessionsScreen evidence={blockedExceptionConcessionsFixture} />);

    expect(screen.getByRole('alert')).toHaveTextContent(/BLOCKED UPSTREAM CONTRACT REQUIRED/i);
    expect(screen.getByText('No concession requests match the current filters.')).toBeInTheDocument();
    expect(stateForExceptionConcessions(blockedExceptionConcessionsFixture)).toBe('blocked');
  });
});
