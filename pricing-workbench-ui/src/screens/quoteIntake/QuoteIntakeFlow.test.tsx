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
      { groupId: 'property', label: 'Property', helpText: 'property', fields: [field('state', false), field('propertyZip', false), field('propertyType', false), field('occupancyType', false), field('purchasePrice', false, 'number')] },
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
  it('singlePageRendersAllSectionsWithoutStepNavigation', () => {
    render(<QuoteIntakeFlow metadataState={metadataState} />);
    expect(screen.getByRole('heading', { name: /Pipeline Intake/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Loan Basics/i })).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByRole('button', { name: /Borrower & Credit/i })).toHaveAttribute('aria-expanded', 'false');
    expect(screen.queryByRole('button', { name: /Previous/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Next/i })).not.toBeInTheDocument();
  });

  it('loanPassStartFieldsAreRequiredAndPreferencesStepIsRemoved', () => {
    render(<QuoteIntakeFlow metadataState={metadataState} />);
    const loanBasics = screen.getByRole('region', { name: /Loan Basics/i });
    expect(within(loanBasics).getByRole('textbox', { name: /^Borrower last name/i })).toBeRequired();
    expect(within(loanBasics).getByRole('textbox', { name: /^Loan number/i })).toBeRequired();
    expect(within(loanBasics).getByRole('combobox', { name: /^Mortgage type/i })).toBeRequired();
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

  it('sectionValidationOnExpandAndLaunchesQuoteFromBottom', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = input.toString();
      if (url.endsWith('/scenarios') && init?.method === 'POST') return json({ scenarioId: 'scenario-launch', scenarioVersion: 1 });
      if (url.endsWith('/income-assets/validate')) return json({ passed: true, status: 'PASSED', message: 'ok', blockers: {} });
      if (url.endsWith('/quote-runs') && init?.method === 'POST') return json({ status: 'CREATED', runId: 'run-1', nextRoute: '/quote/run-1/offers', validationSummary: { passed: true, status: 'PASSED', message: 'ok', blockers: {} }, uiTraceId: 'trace', events: [], fallbackMode: false, dependencyStatus: '', auditPackageId: null, replayHashRef: null, validationIssues: [] });
      return json({ scenarioId: 'scenario-launch', scenarioVersion: 2, passed: true, status: 'PASSED', message: 'ok', blockers: {} });
    });
    const onNavigate = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    render(<QuoteIntakeFlow metadataState={metadataState} intake={{ ...filledIntake(), borrowerLastName: 'Alex', loanNumber: 'LP-1001', mortgageType: 'Conventional', propertyZip: '90001', contactEmail: 'alex@example.test', state: 'CA' }} onNavigate={onNavigate} />);

    fireEvent.click(screen.getByRole('button', { name: /Borrower & Credit/i }));
    expect(screen.getByRole('textbox', { name: /^Borrower first name/i })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /Borrower & Credit/i }));
    const launchButtons = screen.getAllByRole('button', { name: /^Launch Quote$/i });
    fireEvent.click(launchButtons[launchButtons.length - 1]);
    await waitFor(() => expect(onNavigate).toHaveBeenCalledWith('/quote/run-1/offers'));
  });

  it('responsiveAt320pxUsesSingleColumnSinglePageShell', () => {
    const { container } = render(<QuoteIntakeFlow metadataState={metadataState} />);
    expect(container.querySelector('.quote-intake-shell--single-page')).toBeInTheDocument();
    expect(container.querySelector('.quote-intake-form--single-page')).toBeInTheDocument();
  });

  it('desktopPipelineKeepsSectionsOnResponsiveSinglePageSurface', () => {
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 1280 });
    const { container } = render(<QuoteIntakeFlow metadataState={metadataState} />);

    expect(container.querySelector('.quote-intake-shell--single-page')).toBeInTheDocument();
    expect(container.querySelector('.quote-intake-form--single-page')).toBeInTheDocument();
    expect(container.querySelectorAll('.quote-intake-section')).toHaveLength(5);
  });

  it('hidesValidationErrorsUntilBlurOrSubmit', () => {
    render(<QuoteIntakeFlow metadataState={metadataState} errors={{ loanNumber: 'Loan number is required.' }} />);

    const loanNumber = screen.getByRole('textbox', { name: /^Loan number/i });
    expect(loanNumber).toHaveAttribute('aria-invalid', 'false');
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();

    fireEvent.blur(loanNumber);

    expect(loanNumber).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByRole('alert')).toHaveTextContent('Loan Number is required.');
  });

  it('rendersLoanPassFieldsAsFriendlySelectsAndConsolidatesZip', () => {
    render(<QuoteIntakeFlow metadataState={metadataState} intake={{ ...initialQuoteIntake, propertyType: 'Single Family', occupancyType: 'Primary Residence', purchasePrice: '-1', appraisedValue: '-1' }} />);

    fireEvent.click(screen.getByRole('button', { name: /Property/i }));

    expect(screen.getByRole('combobox', { name: /^Property type$/i })).toHaveDisplayValue('Single Family');
    expect(screen.getByRole('combobox', { name: /^Occupancy type$/i })).toHaveDisplayValue('Primary Residence');
    expect(screen.getAllByRole('textbox', { name: /^Property ZIP$/i })).toHaveLength(1);

    fireEvent.click(screen.getByRole('button', { name: /Income & Assets/i }));

    expect(screen.getByRole('combobox', { name: /^State$/i })).toHaveDisplayValue('Select state');
    expect(screen.getByRole('combobox', { name: /^Documentation type$/i })).toHaveDisplayValue('Select documentation type');
    expect(screen.getByRole('combobox', { name: /^Self Employed$/i })).toHaveDisplayValue('Select self-employed status');
    expect(screen.getByRole('spinbutton', { name: /^Purchase price$/i })).toHaveValue(null);
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
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
