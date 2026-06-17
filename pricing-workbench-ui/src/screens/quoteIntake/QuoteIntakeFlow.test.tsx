import '@testing-library/jest-dom/vitest';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { QuoteIntakeFlow, initialQuoteIntake } from './QuoteIntakeFlow';
import type { BorrowerIntake, MetadataState, ScenarioIntakeField } from '../../lib/api/quoteRuns';

const metadataState: MetadataState = {
  kind: 'loaded',
  metadata: {
    tenantContext: 'ui-preview-tenant',
    dependencyStatus: 'READY',
    decisionControls: [],
    validationIssues: [],
    auditPackageId: 'audit',
    replayHashRef: 'replay',
    fallbackReason: '',
    uiTraceId: 'brw-s01-local-trace',
    quickQuoteState: {
      minimalFirstStepFields: ['borrowerLastName', 'loanNumber', 'mortgageType'],
      progressiveSectionOrder: ['scenario-identity', 'borrower-credit', 'loan-structure', 'property', 'income-assets'],
      quoteServiceRequiredFacts: ['scenarioId', 'scenarioVersion'],
      backendOwnedFactSources: ['scenario-service', 'quote-service'],
      blockedByContracts: [],
      fallbackReason: '',
    },
    fieldGroups: [
      { groupId: 'scenario-identity', label: 'Loan Basics', helpText: 'basics', fields: [field('borrowerLastName', true), field('loanNumber', true), field('mortgageType', true)] },
      { groupId: 'borrower-credit', label: 'Borrower Credit', helpText: 'borrower', fields: [field('borrowerFirstName', false), field('contactEmail', false, 'email'), field('decisionCreditScore', false, 'number')] },
      { groupId: 'loan-structure', label: 'Loan Structure', helpText: 'loan', fields: [field('baseLoanAmount', false, 'number'), field('desiredLoanTerm', false, 'number'), field('desiredAmortizationType', false)] },
      { groupId: 'property', label: 'Property', helpText: 'property', fields: [field('state', false), field('zip', false), field('propertyType', false), field('occupancyType', false), field('purchasePrice', false, 'number')] },
      { groupId: 'income-assets', label: 'Income Assets', helpText: 'income', fields: [field('totalBorrowerIncome', false, 'number'), field('documentationType', false), field('selfEmployed', false)] },
    ],
  },
};

afterEach(() => {
  cleanup();
  window.localStorage.clear();
  vi.unstubAllGlobals();
});

describe('PipelineIntakeTest', () => {
  it('rendersSplitViewProductFinderWithoutStepNavigation', () => {
    render(<QuoteIntakeFlow metadataState={metadataState} />);
    expect(screen.getByRole('heading', { name: /^Intake$/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /^Filters$/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /^Products$/i })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Previous/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Next/i })).not.toBeInTheDocument();
  });

  it('onlyLoanPassStartKeysAreRequiredAndPreferencesStepIsRemoved', () => {
    render(<QuoteIntakeFlow metadataState={metadataState} />);
    const startPipeline = screen.getByRole('heading', { name: /^Keys$/i }).closest('section') as HTMLElement;
    expect(within(startPipeline).getByRole('textbox', { name: /^Borrower last name/i })).toBeRequired();
    expect(within(startPipeline).getByRole('textbox', { name: /^Loan number/i })).toBeRequired();
    expect(screen.getByRole('combobox', { name: /^Mortgage type/i })).not.toBeRequired();
    expect(screen.queryByRole('button', { name: /Preferences & Launch/i })).not.toBeInTheDocument();
  });

  it('loanBasicsCreatesDraftOnFirstFieldEntryAndSavesOnBlur', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = input.toString();
      if (url.endsWith('/scenarios') && init?.method === 'POST') return json({ scenarioId: 'scenario-1', scenarioVersion: 1 });
      if (url.endsWith('/scenario-identity') && init?.method === 'PATCH') return json({ scenarioId: 'scenario-1', scenarioVersion: 2 });
      return json({});
    });
    vi.stubGlobal('fetch', fetchMock);
    render(<QuoteIntakeFlow metadataState={metadataState} />);

    fireEvent.change(screen.getByRole('textbox', { name: /Loan number/i }), { target: { value: 'LP-1001' } });
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/v1/tenants/ui-preview-tenant/scenarios', expect.objectContaining({ method: 'POST' })));
    const firstDraftCreate = fetchMock.mock.calls.find(([input, init]) => input.toString().endsWith('/scenarios') && init?.method === 'POST');
    expect(JSON.parse(String(firstDraftCreate?.[1]?.body))).toMatchObject({ data: { loanNumber: 'LP-1001' } });
    fireEvent.blur(screen.getByRole('textbox', { name: /Loan number/i }));
    await waitFor(() => expect(window.localStorage.getItem('wcpe:quoteIntakeDraft:last')).toBe('scenario-1'));
  });

  it('launchesQuoteFromProductFinderWithMinimumStartKeys', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = input.toString();
      if (url.endsWith('/scenarios') && init?.method === 'POST') return json({ scenarioId: 'scenario-launch', scenarioVersion: 1 });
      if (url.endsWith('/quote-runs') && init?.method === 'POST') return json({ status: 'CREATED', runId: 'run-1', nextRoute: '/quote/run-1/offers', validationSummary: { passed: true, status: 'PASSED', message: 'ok', blockers: {} }, uiTraceId: 'trace', events: [], fallbackMode: false, dependencyStatus: '', auditPackageId: null, replayHashRef: null, validationIssues: [] });
      return json({ scenarioId: 'scenario-launch', scenarioVersion: 2, passed: true, status: 'PASSED', message: 'ok', blockers: {} });
    });
    const onNavigate = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    render(<QuoteIntakeFlow metadataState={metadataState} intake={{ ...filledIntake(), borrowerLastName: 'Alex', loanNumber: 'LP-1001', mortgageType: 'Conventional', zip: '90001', contactEmail: 'alex@example.test', state: 'CA' }} onNavigate={onNavigate} />);

    expect(screen.getByRole('heading', { name: /^Products$/i })).toBeInTheDocument();
    fireEvent.click(screen.getAllByRole('button', { name: /^Use$/i })[0]);
    const launchButton = screen.getByRole('button', { name: /^Launch Quote$/i });
    await waitFor(() => expect(launchButton).not.toBeDisabled());
    fireEvent.click(launchButton);
    await waitFor(() => expect(onNavigate).toHaveBeenCalledWith('/quote/run-1/offers'));
  });

  it('responsiveAt320pxUsesSingleColumnSinglePageShell', () => {
    const { container } = render(<QuoteIntakeFlow metadataState={metadataState} />);
    expect(container.querySelector('.quote-intake-shell--single-page')).toBeInTheDocument();
    expect(container.querySelector('.quote-intake-form--single-page')).toBeInTheDocument();
  });

  it('desktopPipelineUsesThirtySeventySplitViewSurface', () => {
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 1280 });
    const { container } = render(<QuoteIntakeFlow metadataState={metadataState} />);

    expect(container.querySelector('.quote-intake-shell--single-page')).toBeInTheDocument();
    expect(container.querySelector('.quote-intake-form--product-finder')).toBeInTheDocument();
    expect(container.querySelector('.quote-product-filter-panel')).toBeInTheDocument();
    expect(container.querySelector('.quote-product-results-panel')).toBeInTheDocument();
    expect(container.querySelectorAll('.quote-intake-section')).toHaveLength(0);
  });

  it('hidesValidationErrorsUntilSubmit', () => {
    render(<QuoteIntakeFlow metadataState={metadataState} errors={{ loanNumber: 'Loan number is required.' }} />);

    const loanNumber = screen.getByRole('textbox', { name: /^Loan number/i });
    expect(loanNumber).toHaveAttribute('aria-invalid', 'false');
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /^Save Pipeline$/i }));

    expect(loanNumber).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getAllByRole('alert').map((alert) => alert.textContent)).toEqual(expect.arrayContaining(['Loan Number is required.']));
  });

  it('savesAndRetrievesPipelineThroughDraftScenarioLookup', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = input.toString();
      if (url.endsWith('/scenarios') && init?.method === 'POST') return json({ scenarioId: 'scenario-pipeline-1', scenarioVersion: 1, intake: { borrowerLastName: 'Rivera', loanNumber: 'LN-2001' } });
      if (url.includes('/scenarios?') && url.includes('borrowerLastName=Rivera') && url.includes('loanNumber=LN-2001')) return json({ records: [{ scenarioId: 'scenario-pipeline-1', scenarioVersion: 2, status: 'DRAFT_INCOMPLETE', intake: { borrowerLastName: 'Rivera', loanNumber: 'LN-2001', loanPurpose: 'Purchase' } }] });
      return json({ scenarioId: 'scenario-pipeline-1', scenarioVersion: 2, intake: {} });
    });
    vi.stubGlobal('fetch', fetchMock);
    render(<QuoteIntakeFlow metadataState={metadataState} />);

    fireEvent.change(screen.getByRole('textbox', { name: /^Borrower last name/i }), { target: { value: 'Rivera' } });
    fireEvent.change(screen.getByRole('textbox', { name: /^Loan number/i }), { target: { value: 'LN-2001' } });
    fireEvent.click(screen.getByRole('button', { name: /^Save Pipeline$/i }));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/v1/tenants/ui-preview-tenant/scenarios', expect.objectContaining({ method: 'POST' })));
    await waitFor(() => expect(fetchMock.mock.calls.some(([, init]) => String(init?.body ?? '').includes('LN-2001'))).toBe(true));
    const persistCall = [...fetchMock.mock.calls].reverse().find(([, init]) => String(init?.body ?? '').includes('LN-2001'));
    expect(JSON.parse(String(persistCall?.[1]?.body))).toMatchObject({ data: { borrowerLastName: 'Rivera', loanNumber: 'LN-2001' } });

    fireEvent.click(screen.getByRole('button', { name: /^Retrieve Pipeline$/i }));
    const dialog = screen.getByRole('dialog', { name: /^Retrieve Pipeline$/i });
    fireEvent.change(within(dialog).getByLabelText(/Borrower Last Name/i), { target: { value: 'Rivera' } });
    fireEvent.change(within(dialog).getByLabelText(/Loan Number/i), { target: { value: 'LN-2001' } });
    fireEvent.click(within(dialog).getByRole('button', { name: /^Retrieve$/i }));

    await waitFor(() => expect(fetchMock.mock.calls.some(([input]) => input.toString().includes('/scenarios?borrowerLastName=Rivera&loanNumber=LN-2001'))).toBe(true));
    await waitFor(() => expect(screen.queryByRole('dialog', { name: /^Retrieve Pipeline$/i })).not.toBeInTheDocument());
    expect(screen.getByRole('status')).toHaveTextContent(/Retrieved scenario-pipeline-1/i);
  });

  it('rendersLoanPassFieldsAsFriendlySelectsAndConsolidatesZip', () => {
    render(<QuoteIntakeFlow metadataState={metadataState} intake={{ ...initialQuoteIntake, propertyType: 'Single Family', occupancyType: 'Primary Residence', purchasePrice: '-1', appraisedValue: '-1' }} />);

    expect(screen.getAllByRole('textbox', { name: /^Property ZIP$/i })).toHaveLength(1);

    expect(screen.getByRole('combobox', { name: /^State$/i })).toHaveDisplayValue('Select state');
    expect(screen.getByRole('combobox', { name: /^Documentation type$/i })).toHaveDisplayValue('Select documentation type');
    expect(screen.getByRole('spinbutton', { name: /^Decision credit score$/i })).toHaveValue(null);
    expect(screen.getByRole('spinbutton', { name: /^Base loan amount$/i })).toHaveValue(null);
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('filtersProductResultsFromLeftPanelControls', () => {
    render(<QuoteIntakeFlow metadataState={metadataState} />);

    expect(screen.getByRole('listitem', { name: /VA 30-Year Preview ACTIVE/i })).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText(/^Mortgage type$/i), { target: { value: 'FHA' } });
    expect(screen.queryByRole('listitem', { name: /VA 30-Year Preview ACTIVE/i })).not.toBeInTheDocument();
    expect(screen.getByRole('listitem', { name: /FHA 30-Year Preview PENDING/i })).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText(/^Status$/i), { target: { value: 'ACTIVE' } });
    expect(screen.getByText(/No matches/i)).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText(/^Status$/i), { target: { value: 'PENDING' } });
    expect(screen.getByRole('listitem', { name: /FHA 30-Year Preview PENDING/i })).toBeInTheDocument();
  });
});

function field(fieldId: keyof BorrowerIntake, required: boolean, dataType: ScenarioIntakeField['dataType'] = 'text'): ScenarioIntakeField {
  return { fieldId, label: String(fieldId).replace(/[A-Z]/g, ' $&').replace(/^./, (c) => c.toUpperCase()), groupId: 'test', dataType, required, helpText: `${fieldId} help`, sourceRef: 'metadata', decisionQuality: 'VERIFIED', validationMessages: [] };
}

function filledIntake(): BorrowerIntake {
  return Object.fromEntries(Object.keys(initialQuoteIntake).map((key) => [key, key === 'contactEmail' ? 'alex@example.test' : '1'])) as BorrowerIntake;
}

function json(body: unknown) {
  return { ok: true, status: 200, json: async () => body } as Response;
}
