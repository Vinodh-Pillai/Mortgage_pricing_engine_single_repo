import '@testing-library/jest-dom/vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { fetchScenarioIntakeMetadata, launchQuoteRun as launchQuoteRunClient, PipelineApiResponseError } from '../../../src/lib/api/quoteRuns';
import { QuoteIntakeFlow } from '../../../src/screens/quoteIntake/QuoteIntakeFlow';
import { launchQuoteRun as launchPipelineQuoteRun } from '../../../src/screens/quoteIntake/launch';
import { blockedLaunchResponse, jsonResponse, loanPassMetadata, loanPassMinimumIntake, loanPassTenantId, textResponse } from './loanpass-test-fixtures';

describe('PII-26-S17 LoanPass local error handling coverage', () => {
  it('surfaces deterministic PipelineApiResponseError details for metadata service outages', async () => {
    const fetchMock = vi.fn(async () => textResponse('upstream unavailable', 500));

    await expect(fetchScenarioIntakeMetadata(loanPassTenantId, fetchMock as typeof fetch)).rejects.toMatchObject({
      name: 'PipelineApiResponseError',
      status: 500,
      endpointContext: 'scenario intake metadata',
      message: expect.stringContaining('Scenario intake metadata is temporarily unavailable.'),
    });
  });

  it('surfaces actionable endpoint context for non-JSON auth failures without exposing response bodies', async () => {
    const fetchMock = vi.fn(async () => textResponse('login required', 401));

    await expect(launchQuoteRunClient(loanPassTenantId, loanPassMinimumIntake, fetchMock as typeof fetch)).rejects.toBeInstanceOf(PipelineApiResponseError);
    await expect(launchQuoteRunClient(loanPassTenantId, loanPassMinimumIntake, fetchMock as typeof fetch)).rejects.toMatchObject({
      status: 401,
      endpointContext: 'quote run launch',
      message: expect.stringContaining('non-JSON response body'),
    });
  });

  it('keeps LoanPass 422 field blockers in the UI launch result for parent UI mapping', async () => {
    const fetchMock = vi.fn(async () => jsonResponse(blockedLaunchResponse(), 422));

    const result = await launchPipelineQuoteRun(loanPassTenantId, 'scenario-loanpass-1', 1, loanPassMinimumIntake, fetchMock as typeof fetch);

    expect(result.kind).toBe('blocked');
    if (result.kind === 'blocked') {
      expect(result.validation.blockers).toEqual({ borrowerLastName: 'Borrower last name is required.' });
      expect(result.blockers).toEqual(['borrowerLastName']);
    }
  });

  it('renders incomplete local LoanPass launch as accessible validation errors before any network launch call', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = input.toString();
      if (url.endsWith('/scenarios') && init?.method === 'POST') return jsonResponse({ scenarioId: 'scenario-loanpass-1', scenarioVersion: 1 });
      return jsonResponse({ scenarioId: 'scenario-loanpass-1', scenarioVersion: 2, passed: true, status: 'PASSED', message: 'ok', blockers: {} });
    });

    vi.stubGlobal('fetch', fetchMock);
    render(<QuoteIntakeFlow metadataState={{ kind: 'loaded', metadata: loanPassMetadata() }} intake={{ ...loanPassMinimumIntake, borrowerLastName: '' }} />);

    fireEvent.click(screen.getAllByRole('button', { name: /^Launch Quote$/i }).at(-1)!);

    await waitFor(() => expect(screen.getAllByRole('alert').some((alert) => alert.textContent?.includes('Borrower Last Name is required.'))).toBe(true));
    expect(screen.getByRole('textbox', { name: /^Borrower Last Name/i })).toHaveAttribute('aria-invalid', 'true');
    expect(fetchMock.mock.calls.some(([input]) => input.toString().endsWith('/quote-runs'))).toBe(false);
  });
});
