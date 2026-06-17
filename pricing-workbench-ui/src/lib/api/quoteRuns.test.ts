import { describe, expect, it, vi } from 'vitest';
import { fetchScenarioIntakeMetadata, launchQuoteRun, PipelineApiResponseError, type BorrowerIntake, type QuoteRunLaunch, type ScenarioIntakeMetadata } from './quoteRuns';

describe('quoteRuns pipeline API response handling', () => {
  it('preserves successful JSON response behavior', async () => {
    const launch: QuoteRunLaunch = {
      runId: 'run-1',
      status: 'CREATED',
      nextRoute: '/quote/run-1/offers',
      validationSummary: { passed: true, status: 'PASSED', message: 'ok', blockers: {} },
      uiTraceId: 'trace',
      events: [],
      fallbackMode: false,
      dependencyStatus: 'READY',
      auditPackageId: null,
      replayHashRef: null,
      validationIssues: [],
    };
    const fetchMock = vi.fn(async () => jsonResponse(launch));

    await expect(launchQuoteRun('tenant-a', intake(), fetchMock)).resolves.toEqual(launch);
  });

  it('preserves typed JSON error payload behavior for client-side blocked launches', async () => {
    const blocked: QuoteRunLaunch = {
      runId: null,
      status: 'BLOCKED',
      nextRoute: null,
      validationSummary: { passed: false, status: 'BLOCKED', message: 'missing facts', blockers: { loanAmount: 'Loan amount is required.' } },
      uiTraceId: 'trace',
      events: [],
      fallbackMode: false,
      dependencyStatus: 'READY',
      auditPackageId: null,
      replayHashRef: null,
      validationIssues: [],
    };
    const fetchMock = vi.fn(async () => jsonResponse(blocked, 400));

    await expect(launchQuoteRun('tenant-a', intake(), fetchMock)).resolves.toEqual(blocked);
  });

  it('does not call response.json blindly for no-content responses', async () => {
    const fetchMock = vi.fn(async () => new Response(null, { status: 204 }));

    await expect(fetchScenarioIntakeMetadata('tenant-a', fetchMock)).rejects.toMatchObject({
      name: 'PipelineApiResponseError',
      status: 204,
      endpointContext: 'scenario intake metadata',
    });
  });

  it('surfaces status and endpoint context for empty error bodies', async () => {
    const fetchMock = vi.fn(async () => new Response('', { status: 503 }));

    await expect(launchQuoteRun('tenant-a', intake(), fetchMock)).rejects.toThrow(/HTTP 503.*quote run launch.*empty response body/i);
  });

  it('surfaces bounded actionable errors for non-JSON error bodies', async () => {
    const fetchMock = vi.fn(async () => new Response('<html>gateway</html>', { status: 502, headers: { 'Content-Type': 'text/html' } }));

    await expect(launchQuoteRun('tenant-a', intake(), fetchMock)).rejects.toThrow(/HTTP 502.*quote run launch.*non-JSON response body/i);
  });

  it('does not expose raw non-JSON response bodies in API errors', async () => {
    const fetchMock = vi.fn(async () => new Response('token=secret-value', { status: 500, headers: { 'Content-Type': 'text/plain' } }));

    try {
      await launchQuoteRun('tenant-a', intake(), fetchMock);
      throw new Error('expected launchQuoteRun to reject');
    } catch (error) {
      expect(error).toBeInstanceOf(PipelineApiResponseError);
      expect((error as Error).message).toContain('non-JSON response body');
      expect((error as Error).message).not.toContain('secret-value');
    }
  });
});

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

function intake(): BorrowerIntake {
  return {
    quoteIntent: 'purchase',
    channel: 'retail',
    scenarioName: 'Pipeline error handling test',
    externalLoanId: 'loan-1',
    sourceSystem: 'test',
    borrowerName: 'Alex',
    borrowerRole: 'borrower',
    coBorrowerName: '',
    coBorrowerRole: '',
    contactEmail: 'alex@example.test',
    creditStatus: 'known',
    creditScore: '720',
    creditScoreSource: 'borrower',
    creditReportDate: '2026-06-17',
    creditReadiness: 'ready',
    loanPurpose: 'purchase',
    loanAmount: '450000',
    purchasePriceOrValue: '500000',
    downPaymentOrEquity: '50000',
    subordinateFinancingAmount: '0',
    helocDrawnAmount: '0',
    helocLimitAmount: '0',
    lienPosition: 'first',
    termMonths: '360',
    amortizationType: 'fixed',
    requestedLockPeriodDays: '30',
    propertyState: 'CA',
    propertyCounty: 'Los Angeles',
    propertyZip: '90001',
    propertyType: 'single-family',
    occupancyType: 'primary',
    unitCount: '1',
    purchasePrice: '500000',
    appraisedValue: '500000',
    condoProjectType: '',
    manufacturedHomeFlag: 'false',
    monthlyIncome: '12000',
    incomeType: 'w2',
    employmentType: 'employed',
    monthlyDebt: '1000',
    suppliedDti: '20',
    reserveMonths: '6',
    incomeVerificationStatus: 'verified',
    assetVerificationStatus: 'verified',
    liquidAssets: '100000',
    reserves: '50000',
    productFamily: 'conventional',
    productPreference: 'fixed',
    quoteFilters: '',
    effectiveDate: '2026-06-17',
    actorId: 'tester',
    clientContext: 'vitest',
  };
}

const _metadataCompileCheck: Partial<ScenarioIntakeMetadata> = {};
