import { describe, expect, it, vi } from 'vitest';
import { fetchScenarioIntakeMetadata, launchQuoteRun, PipelineApiResponseError, toLoanPassQuoteIntakePayload, type BorrowerIntake, type QuoteRunLaunch, type ScenarioIntakeMetadata } from './quoteRuns';

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
      validationSummary: { passed: false, status: 'BLOCKED', message: 'missing facts', blockers: { baseLoanAmount: 'Base loan amount is required.' } },
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

  it('serializes only LoanPass-aligned intake fields for quote launch', async () => {
    const fetchMock = vi.fn(async () => jsonResponse({
      runId: 'run-1',
      status: 'CREATED',
      nextRoute: null,
      validationSummary: { passed: true, status: 'PASSED', message: 'ok', blockers: {} },
      uiTraceId: 'trace',
      events: [],
      fallbackMode: false,
      dependencyStatus: 'READY',
      auditPackageId: null,
      replayHashRef: null,
      validationIssues: [],
    }));

    await launchQuoteRun('tenant-a', intake(), fetchMock);

    const payload = JSON.parse(String(fetchMock.mock.calls[0][1]?.body));
    expect(payload).toEqual(toLoanPassQuoteIntakePayload(intake()));
    expect(payload.borrowerLastName).toBe('Johnson');
    expect(payload.loanNumber).toBe('LP-1001');
    expect(payload.mortgageType).toBe('Conventional');
    expect(payload).not.toHaveProperty('borrowerName');
    expect(payload).not.toHaveProperty('loanAmount');
    expect(payload).not.toHaveProperty('purchasePriceOrValue');
    expect(payload).not.toHaveProperty('productFamily');
  });
});

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

function intake(): BorrowerIntake {
  return {
    channel: 'Retail',
    loanNumber: 'LP-1001',
    borrowerFirstName: 'Alex',
    borrowerLastName: 'Johnson',
    numberOfBorrowers: '1',
    contactEmail: 'alex@example.test',
    creditStatus: 'known',
    decisionCreditScore: '720',
    citizenshipType: 'US Citizen',
    professional: 'No',
    loanPurpose: 'purchase',
    baseLoanAmount: '450000',
    downPaymentOrEquity: '50000',
    lienPosition: 'First',
    desiredLoanTerm: '30',
    desiredAmortizationType: 'Fixed',
    desiredRateLockPeriod: '30',
    desiredInterestRate: '',
    prepaymentPenaltyTerm: 'No Prepay',
    waiveEscrows: 'No',
    interestOnly: 'No',
    mortgageType: 'Conventional',
    state: 'CA',
    propertyZip: '90001',
    propertyType: 'Single Family',
    occupancyType: 'Primary Residence',
    numberOfUnits: '1',
    propertyLocation: 'Not Applicable',
    numberOfLeasedUnits: '0',
    shortTermRental: 'No',
    monthlyMarketRent: '',
    investorExperience: 'Non-Experienced',
    additionalMonthlyHousingExpenses: '',
    propertySquareFootage: '',
    propertyAcreageNumber: '',
    monthlyTaxes: '',
    monthlyInsurance: '',
    monthlyHOA: '',
    additionalAnnualHousingExpenses: '',
    annualTaxes: '',
    annualInsurance: '',
    annualHOA: '',
    purchasePrice: '500000',
    appraisedValue: '500000',
    selfEmployed: 'No',
    totalBorrowerIncome: '12000',
    monthlyDebt: '1000',
    estimatedDti: '20',
    monthsOfReserves: '6',
    liquidAssets: '100000',
    documentationType: 'Full Documentation',
    secondaryDocumentationType: 'None',
    estimatedDscr: '',
    gift: 'No',
    achPayment: 'No',
    mortgageLatePayments: 'No',
    creditEvent: 'No',
    wholesaleCompensation: 'Borrower Paid',
    lockExtension: '',
    lockExtension2: '',
    concession: 'No',
    secondaryAdjustment: 'No',
    aus: 'None',
    manualUnderwriting: 'No',
  };
}

const _metadataCompileCheck: Partial<ScenarioIntakeMetadata> = {};
