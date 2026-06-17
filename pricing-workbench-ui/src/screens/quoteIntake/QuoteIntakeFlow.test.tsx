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
      minimalFirstStepFields: ['quoteIntent', 'channel', 'scenarioName', 'externalLoanId'],
      progressiveSectionOrder: ['scenario-identity', 'borrower-credit', 'loan-structure', 'property', 'income-assets', 'preferences'],
      quoteServiceRequiredFacts: ['scenarioId', 'scenarioVersion'],
      backendOwnedFactSources: ['scenario-service', 'quote-service'],
      blockedByContracts: [],
      fallbackReason: '',
    },
    fieldGroups: [
      { groupId: 'scenario-identity', label: 'Loan Basics', helpText: 'basics', fields: [field('loanPurpose', true), field('loanAmount', true, 'number'), field('purchasePriceOrValue', true, 'number'), field('propertyZip', true)] },
      { groupId: 'borrower-credit', label: 'Borrower Credit', helpText: 'borrower', fields: [field('borrowerName', true), field('contactEmail', true, 'email')] },
      { groupId: 'loan-structure', label: 'Loan Structure', helpText: 'loan', fields: [field('termMonths', false, 'number')] },
      { groupId: 'property', label: 'Property', helpText: 'property', fields: [field('propertyState', true), field('propertyZip', true), field('purchasePrice', false, 'number')] },
      { groupId: 'income-assets', label: 'Income Assets', helpText: 'income', fields: [field('monthlyIncome', false, 'number'), field('incomeType', false, 'number'), field('incomeVerificationStatus', false)] },
      { groupId: 'preferences', label: 'Preferences', helpText: 'launch', fields: [field('productFamily', false), field('effectiveDate', false), field('quoteIntent', true), field('channel', true), field('scenarioName', false), field('externalLoanId', false)] },
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

  it('technicalFieldsOptionalAndAvailableInPreferences', () => {
    render(<QuoteIntakeFlow metadataState={metadataState} />);
    fireEvent.click(screen.getByRole('button', { name: /Preferences & Launch/i }));
    const preferences = screen.getByRole('region', { name: /Preferences & Launch/i });
    expect(within(preferences).getByRole('textbox', { name: /^Quote intent$/i })).toBeInTheDocument();
    expect(within(preferences).getByRole('combobox', { name: /^Channel$/i })).toBeInTheDocument();
    expect(within(preferences).getByRole('textbox', { name: /^Scenario name$/i })).toBeInTheDocument();
    expect(within(preferences).getByRole('textbox', { name: /^External loan ID$/i })).toBeInTheDocument();
    expect(within(preferences).getByLabelText(/^Quote intent$/i)).not.toBeRequired();
    expect(within(preferences).getByLabelText(/^Channel$/i)).not.toBeRequired();
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

    fireEvent.change(screen.getByRole('textbox', { name: /Loan purpose/i }), { target: { value: 'Purchase' } });
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/v1/tenants/ui-preview-tenant/scenarios', expect.objectContaining({ method: 'POST' })));
    const firstDraftCreate = fetchMock.mock.calls.find(([input, init]) => input.toString().endsWith('/scenarios') && init?.method === 'POST');
    expect(JSON.parse(String(firstDraftCreate?.[1]?.body))).toMatchObject({ data: { loanPurpose: 'Purchase' } });
    fireEvent.blur(screen.getByRole('textbox', { name: /Loan purpose/i }));
    await waitFor(() => expect(window.localStorage.getItem('wcpe:quoteIntakeDraft:last')).toBe('scenario-1'));
  });

  it('sectionValidationOnExpandAndLaunchesQuoteFromBottom', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = input.toString();
      if (url.endsWith('/scenarios') && init?.method === 'POST') return json({ scenarioId: 'scenario-launch', scenarioVersion: 1 });
      if (url.endsWith('/preferences/validate')) return json({ passed: true, status: 'PASSED', message: 'ok', blockers: {} });
      if (url.endsWith('/quote-runs') && init?.method === 'POST') return json({ status: 'CREATED', runId: 'run-1', nextRoute: '/quote/run-1/offers', validationSummary: { passed: true, status: 'PASSED', message: 'ok', blockers: {} }, uiTraceId: 'trace', events: [], fallbackMode: false, dependencyStatus: '', auditPackageId: null, replayHashRef: null, validationIssues: [] });
      return json({ scenarioId: 'scenario-launch', scenarioVersion: 2, passed: true, status: 'PASSED', message: 'ok', blockers: {} });
    });
    const onNavigate = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    render(<QuoteIntakeFlow metadataState={metadataState} intake={{ ...filledIntake(), loanPurpose: 'Purchase', loanAmount: '450000', purchasePriceOrValue: '500000', propertyZip: '90001', borrowerName: 'Alex', contactEmail: 'alex@example.test', propertyState: 'CA' }} onNavigate={onNavigate} />);

    fireEvent.click(screen.getByRole('button', { name: /Borrower & Credit/i }));
    expect(screen.getByRole('textbox', { name: /^Borrower name/i })).toBeInTheDocument();
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
    expect(container.querySelectorAll('.quote-intake-section')).toHaveLength(6);
  });

  it('hidesValidationErrorsUntilBlurOrSubmit', () => {
    render(<QuoteIntakeFlow metadataState={metadataState} errors={{ loanPurpose: 'Loan purpose is required.' }} />);

    const loanPurpose = screen.getByRole('textbox', { name: /^Loan purpose/i });
    expect(loanPurpose).toHaveAttribute('aria-invalid', 'false');
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();

    fireEvent.blur(loanPurpose);

    expect(loanPurpose).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByRole('alert')).toHaveTextContent('Loan Purpose is required.');
  });

  it('rendersDefectFieldsAsFriendlySelectsAndConsolidatesZip', () => {
    render(<QuoteIntakeFlow metadataState={metadataState} intake={{ ...initialQuoteIntake, purchasePrice: '-1', purchasePriceOrValue: '-1' }} />);

    expect(screen.getByRole('combobox', { name: /^Property type$/i })).toHaveDisplayValue('Single Family');
    expect(screen.getByRole('combobox', { name: /^Occupancy type$/i })).toHaveDisplayValue('Primary Residence');
    expect(screen.getAllByRole('textbox', { name: /^Property ZIP$/i })).toHaveLength(1);

    fireEvent.click(screen.getByRole('button', { name: /Property/i }));
    fireEvent.click(screen.getByRole('button', { name: /Income & Assets/i }));
    fireEvent.click(screen.getByRole('button', { name: /Preferences & Launch/i }));

    expect(screen.getByRole('combobox', { name: /^Property state$/i })).toHaveDisplayValue('Select state');
    expect(screen.getByRole('combobox', { name: /^Income type$/i })).toHaveDisplayValue('W-2');
    expect(screen.getByRole('combobox', { name: /^Income verification status$/i })).toHaveDisplayValue('Verified');
    expect(screen.getByRole('combobox', { name: /^Channel$/i })).toHaveDisplayValue('Select channel');
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
