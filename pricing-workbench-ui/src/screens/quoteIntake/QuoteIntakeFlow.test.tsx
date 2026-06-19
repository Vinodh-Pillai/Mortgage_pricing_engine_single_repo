import '@testing-library/jest-dom/vitest';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { QuoteIntakeFlow, initialQuoteIntake } from './QuoteIntakeFlow';
import type { BorrowerIntake, MetadataState, ScenarioIntakeField } from '../../lib/api/quoteRuns';
import { validateFields } from './validation';

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

const tenantConfiguredMetadataState: MetadataState = {
  kind: 'loaded',
  metadata: {
    ...metadataState.metadata,
    settings: {
      priceScenarioTable: {
        columns: [
          { key: 'product', label: 'Product' },
          { key: 'dscr', label: 'DSCR' },
          { key: 'payment', label: 'Payment' },
          { key: 'actions', label: 'Actions' },
        ],
        hiddenColumns: ['payment'],
      },
    },
  },
};

const calculationOutputMetadataState: MetadataState = {
  kind: 'loaded',
  metadata: {
    ...metadataState.metadata,
    settings: {
      priceScenarioTable: {
        columns: [
          { key: 'product', label: 'Product' },
          { key: 'calc@final-interest-rate', label: 'Final interest rate' },
          { key: 'calc@adjusted-price', label: 'Adjusted price output', missingLabel: 'Configured missing output' },
          { key: 'calc@ltv', label: 'Calculated LTV' },
          { key: 'actions', label: 'Actions' },
        ],
      },
      pipelineFields: [
        { fieldId: 'calc@final-interest-rate', label: 'Final interest rate' },
        { fieldId: 'calc@adjusted-price', label: 'Adjusted price output', missingLabel: 'Configured missing output' },
        { fieldId: 'calc@ltv', label: 'Calculated LTV' },
      ],
    },
  },
};

const calculationOutputPermissionMetadataState: MetadataState = {
  kind: 'loaded',
  metadata: {
    ...calculationOutputMetadataState.metadata,
    settings: {
      ...calculationOutputMetadataState.metadata.settings,
      fieldPermissions: {
        hiddenFields: ['calc@ltv'],
      },
    },
  },
};

const lockingConfiguredMetadataState: MetadataState = {
  kind: 'loaded',
  metadata: {
    ...metadataState.metadata,
    settings: {
      lockingFields: {
        lockRequestDateFieldId: 'field@lock-request-date',
        lockExpirationDateFieldId: 'calc@lock-expiration-date',
        extensionFieldId: 'field@lock-extension',
      },
      autoConfirmation: {
        lock: false,
        reprice: false,
        relock: false,
        cancellation: false,
        approvalPath: 'lock-desk-review',
      },
    },
  },
};

const orderedFormMetadataState: MetadataState = {
  kind: 'loaded',
  metadata: {
    ...metadataState.metadata,
    quickQuoteState: {
      ...metadataState.metadata.quickQuoteState,
      progressiveSectionOrder: ['property', 'scenario-identity', 'borrower-credit', 'loan-structure', 'income-assets'],
    },
    fieldGroups: [
      { groupId: 'property', label: 'Property', helpText: 'property', fields: [headerField('field@property-details', 'Property details', 1), orderedField('zip', false, 'text', 2), orderedField('state', false, 'text', 3), { ...orderedField('occupancyType', false, 'text', 4), active: false }] },
      { groupId: 'scenario-identity', label: 'Loan Basics', helpText: 'basics', fields: [headerField('field@loan-details', 'Loan details', 1), orderedField('loanNumber', true, 'text', 2), orderedField('borrowerLastName', true, 'text', 3), orderedField('mortgageType', true, 'text', 4)] },
      { groupId: 'borrower-credit', label: 'Borrower Credit', helpText: 'borrower', fields: [orderedField('borrowerFirstName', false, 'text', 1), orderedField('decisionCreditScore', false, 'number', 2)] },
      { groupId: 'loan-structure', label: 'Loan Structure', helpText: 'loan', fields: [orderedField('baseLoanAmount', false, 'number', 1), orderedField('desiredLoanTerm', false, 'number', 2)] },
      { groupId: 'income-assets', label: 'Income Assets', helpText: 'income', fields: [orderedField('documentationType', false, 'text', 1), orderedField('selfEmployed', false, 'text', 2)] },
    ],
  },
};

const conditionalVisibilityMetadataState: MetadataState = {
  kind: 'loaded',
  metadata: {
    ...metadataState.metadata,
    fieldGroups: metadataState.metadata.fieldGroups.map((group) => ({
      ...group,
      fields: group.fields.map((candidate) => candidate.fieldId === 'baseLoanAmount'
        ? { ...candidate, visibilityCondition: { parentFieldId: 'mortgageType', operator: 'equals', values: ['Conventional'] } }
        : candidate),
    })),
  },
};

const conditionalRequiredMetadataState: MetadataState = {
  kind: 'loaded',
  metadata: {
    ...metadataState.metadata,
    fieldGroups: metadataState.metadata.fieldGroups.map((group) => ({
      ...group,
      fields: group.fields.map((candidate) => candidate.fieldId === 'baseLoanAmount'
        ? { ...candidate, required: true, visibilityCondition: { parentFieldId: 'mortgageType', operator: 'equals', values: ['Conventional'] } }
        : candidate),
    })),
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
  }, 30000);

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

  it('shows concise required indicators and disables launch until the quote is ready', () => {
    render(<QuoteIntakeFlow metadataState={metadataState} />);

    expect(screen.getAllByText('Required').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Required fields').length).toBeGreaterThan(0);
    const launchButton = screen.getByRole('button', { name: /^Launch Quote$/i });
    expect(launchButton).toBeDisabled();
    expect(screen.getByText('Select a product and complete required fields')).toBeInTheDocument();
  });

  it('showsDataRequiredStateFromConfiguredRequiredFieldsAndMovesToNextField', async () => {
    const firstMissing = render(<QuoteIntakeFlow metadataState={metadataState} />);

    const dataRequired = screen.getByRole('heading', { name: /^Complete required fields$/i }).closest('section') as HTMLElement;
    expect(dataRequired).toHaveAttribute('data-required-field-ids', expect.stringContaining('borrowerLastName'));
    expect(dataRequired).toHaveAttribute('data-required-field-ids', expect.stringContaining('loanNumber'));
    expect(dataRequired).toHaveAttribute('data-required-field-ids', expect.stringContaining('mortgageType'));

    fireEvent.click(within(dataRequired).getByRole('button', { name: /^Next Field$/i }));
    await waitFor(() => expect(screen.getByRole('textbox', { name: /^Borrower last name/i })).toHaveFocus());
    firstMissing.unmount();

    render(<QuoteIntakeFlow metadataState={metadataState} intake={{ ...initialQuoteIntake, borrowerLastName: 'Rivera' }} />);
    const nextMissing = screen.getByRole('heading', { name: /^Complete required fields$/i }).closest('section') as HTMLElement;
    fireEvent.click(within(nextMissing).getByRole('button', { name: /^Next Field$/i }));
    await waitFor(() => expect(screen.getByRole('textbox', { name: /^Loan number/i })).toHaveFocus());
  }, 30000);

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
  }, 30000);

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

    expect(screen.getByRole('row', { name: /VA 30-Year Preview ACTIVE/i })).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText(/^Mortgage type$/i), { target: { value: 'FHA' } });
    expect(screen.queryByRole('row', { name: /VA 30-Year Preview ACTIVE/i })).not.toBeInTheDocument();
    expect(screen.getByRole('row', { name: /FHA 30-Year Preview PENDING/i })).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText(/^Status$/i), { target: { value: 'ACTIVE' } });
    expect(screen.getByText(/No matches/i)).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText(/^Status$/i), { target: { value: 'PENDING' } });
    expect(screen.getByRole('row', { name: /FHA 30-Year Preview PENDING/i })).toBeInTheDocument();
  });

  it('rendersProductOptionsAsRowsWithConfiguredPricingColumnsNotCards', () => {
    const { container } = render(<QuoteIntakeFlow metadataState={metadataState} />);

    expect(screen.getByRole('table', { name: /Filtered mortgage products/i })).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: /Adjusted rate/i })).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: /Lock period/i })).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: /Adjusted price/i })).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: /PITIA\/ITIA/i })).toBeInTheDocument();
    expect(screen.getByRole('row', { name: /Conventional 30-Year Preview ACTIVE/i })).toBeInTheDocument();
    expect(container.querySelector('.quote-product-card')).not.toBeInTheDocument();
  });

  it('honorsTenantPriceScenarioTableColumnVisibilityAndOrder', () => {
    render(<QuoteIntakeFlow metadataState={tenantConfiguredMetadataState} />);

    expect(screen.getAllByRole('columnheader').map((header) => header.textContent)).toEqual(['Product', 'DSCR', 'Actions']);
    expect(screen.queryByRole('columnheader', { name: /^Payment$/i })).not.toBeInTheDocument();
  });

  it('bindsCalculationOutputColumnsByFieldIdWithoutGuessingMissingValues', () => {
    render(<QuoteIntakeFlow metadataState={calculationOutputMetadataState} intake={{ ...filledIntake(), 'calc@final-interest-rate': '6.875%', secondaryAdjustment: '', loanToValue: '80.00%' } as BorrowerIntake & Record<string, string>} />);

    expect(screen.getAllByRole('columnheader').map((header) => header.textContent)).toEqual(['Product', 'Final interest rate', 'Adjusted price output', 'Calculated LTV', 'Actions']);
    const row = screen.getByRole('row', { name: /Conventional 30-Year Preview ACTIVE/i });
    expect(within(row).getByText('6.875%')).toBeInTheDocument();
    expect(within(row).getByText('Configured missing output')).toBeInTheDocument();
    expect(within(row).getByText('80.00%')).toBeInTheDocument();
  });

  it('hidesCalculationOutputColumnsDeniedByFieldPermissions', () => {
    render(<QuoteIntakeFlow metadataState={calculationOutputPermissionMetadataState} intake={{ ...filledIntake(), 'calc@final-interest-rate': '6.875%', loanToValue: '80.00%' } as BorrowerIntake & Record<string, string>} />);

    expect(screen.queryByRole('columnheader', { name: /^Calculated LTV$/i })).not.toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: /^Final interest rate$/i })).toBeInTheDocument();
  });

  it('selectsEligibleProductRowsExplicitlyWithoutNavigation', () => {
    const onNavigate = vi.fn();
    render(<QuoteIntakeFlow metadataState={metadataState} onNavigate={onNavigate} />);

    const row = screen.getByRole('row', { name: /Conventional 30-Year Preview ACTIVE/i });
    fireEvent.click(within(row).getByRole('button', { name: /^Use$/i }));

    expect(row).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByRole('status')).toHaveTextContent(/Conventional 30-Year Preview selected/i);
    expect(onNavigate).not.toHaveBeenCalled();
  });

  it('bindsLockRequestFieldsFromMetadataAndRequiresApprovalWhenAutoConfirmationDisabled', () => {
    render(<QuoteIntakeFlow metadataState={lockingConfiguredMetadataState} intake={{ ...filledIntake(), effectiveDate: '2026-06-18', requestedLockPeriodDays: '30', lockExtension: '5' }} />);

    const row = screen.getByRole('row', { name: /Conventional 30-Year Preview ACTIVE/i });
    fireEvent.click(within(row).getByRole('button', { name: /^Request lock$/i }));

    expect(screen.getByText(/Approval required before lock, reprice, relock, or cancellation/i)).toBeInTheDocument();
    expect(screen.getByText(/Approval path: lock-desk-review/i)).toBeInTheDocument();
    expect(screen.getByText('Request date: field@lock-request-date = 2026-06-18')).toBeInTheDocument();
    expect(screen.getByText('Expiration date: calc@lock-expiration-date = 30')).toBeInTheDocument();
    expect(screen.getByText('Extension: field@lock-extension = 5')).toBeInTheDocument();
  });

  it('rendersConfiguredApplicationFormSectionsHeadersAndOmitsRemovedFields', () => {
    const { container } = render(<QuoteIntakeFlow metadataState={orderedFormMetadataState} />);

    const sectionLabels = Array.from(container.querySelectorAll('.quote-form-builder__sections > li > strong')).map((node) => node.textContent);
    expect(sectionLabels[0]).toBe('Property');
    const headerRow = container.querySelector('[data-field-id="field@property-details"][data-value-type="header"]') as HTMLElement;
    expect(headerRow).toBeInTheDocument();
    expect(within(headerRow).getByText(/section header/i)).toBeInTheDocument();
    expect(headerRow.querySelector('input, textarea, select')).not.toBeInTheDocument();
    expect(container.querySelector('[data-field-id="occupancyType"]')).not.toBeInTheDocument();
  });

  it('savesReorderedApplicationFormFieldsToTenantDraftOnly', () => {
    const { container } = render(<QuoteIntakeFlow metadataState={orderedFormMetadataState} tenantId="tenant-alpha" />);
    const loanNumberRow = container.querySelector('[data-field-id="loanNumber"]') as HTMLElement;

    fireEvent.click(within(loanNumberRow).getByRole('button', { name: /^Move up$/i }));
    fireEvent.click(screen.getByRole('button', { name: /^Save form draft order$/i }));

    const saved = JSON.parse(window.localStorage.getItem('wcpe:application-form-builder:tenantalpha:draft-order') ?? '{}');
    expect(saved.tenantId).toBe('tenant-alpha');
    expect(saved.sections['scenario-identity'][0]).toBe('loanNumber');
    expect(window.localStorage.getItem('wcpe:application-form-builder:tenantbeta:draft-order')).toBeNull();
  });

  it('authorsConditionalVisibilityWithParentOperatorsAndEnumValues', () => {
    const { container } = render(<QuoteIntakeFlow metadataState={orderedFormMetadataState} tenantId="tenant-alpha" />);
    const loanPurposeRow = container.querySelector('[data-field-id="baseLoanAmount"]') as HTMLElement;

    fireEvent.change(within(loanPurposeRow).getByLabelText(/Parent field/i), { target: { value: 'mortgageType' } });

    expect(within(loanPurposeRow).getByRole('option', { name: /Any of/i })).toBeInTheDocument();
    expect(within(loanPurposeRow).getByRole('option', { name: /Conventional/i })).toBeInTheDocument();

    fireEvent.change(within(loanPurposeRow).getByLabelText(/Condition value/i), { target: { value: 'CONVENTIONAL' } });
    fireEvent.click(screen.getByRole('button', { name: /^Save form draft order$/i }));

    const saved = JSON.parse(window.localStorage.getItem('wcpe:application-form-builder:tenantalpha:conditional-visibility') ?? '{}');
    expect(saved.conditions.baseLoanAmount).toEqual({ parentFieldId: 'mortgageType', operator: 'equals', values: ['CONVENTIONAL'] });
    expect(screen.getByText(/conditional visibility saved for this tenant/i)).toBeInTheDocument();
  }, 30000);

  it('blocksConditionalSavingThatReferencesHiddenOrRemovedParentFields', () => {
    window.localStorage.setItem('wcpe:application-form-builder:tenantalpha:conditional-visibility', JSON.stringify({ tenantId: 'tenant-alpha', conditions: { baseLoanAmount: { parentFieldId: 'occupancyType', operator: 'equals', values: ['Investment'] } } }));
    render(<QuoteIntakeFlow metadataState={orderedFormMetadataState} tenantId="tenant-alpha" />);

    fireEvent.click(screen.getByRole('button', { name: /^Save form draft order$/i }));

    expect(document.body.textContent).toContain('references hidden or removed parent field occupancyType');
  }, 30000);

  it('appliesConditionalVisibilityWithTheFieldLibraryEvaluatorAtRuntime', () => {
    const hidden = render(<QuoteIntakeFlow metadataState={conditionalVisibilityMetadataState} intake={{ ...filledIntake(), mortgageType: '' }} />);
    expect(hidden.queryByRole('spinbutton', { name: /^Base loan amount/i })).not.toBeInTheDocument();
    hidden.unmount();

    render(<QuoteIntakeFlow metadataState={conditionalVisibilityMetadataState} intake={{ ...filledIntake(), mortgageType: 'Conventional' }} />);

    expect(screen.getByRole('spinbutton', { name: /^Base loan amount/i })).toBeInTheDocument();
  }, 30000);

  it('doesNotCountConditionallyHiddenConfiguredRequiredFieldsAndClearsWhenVisibleRequiredFieldsComplete', () => {
    const hidden = render(<QuoteIntakeFlow metadataState={conditionalRequiredMetadataState} intake={{ ...initialQuoteIntake, borrowerLastName: 'Rivera', loanNumber: 'LN-2001' }} />);
    const hiddenDataRequired = screen.getByRole('heading', { name: /^Complete required fields$/i }).closest('section') as HTMLElement;
    expect(hiddenDataRequired.getAttribute('data-required-field-ids')).toBe('mortgageType');
    expect(hiddenDataRequired.getAttribute('data-required-field-ids')).not.toContain('baseLoanAmount');
    hidden.unmount();

    render(<QuoteIntakeFlow metadataState={conditionalRequiredMetadataState} intake={{ ...filledIntake(), mortgageType: 'Conventional', baseLoanAmount: '100000' }} />);
    expect(screen.queryByRole('heading', { name: /^Complete required fields$/i })).not.toBeInTheDocument();
  }, 30000);

  it('requestsPublishedTenantFormAndRendersRuntimeControlsFromActiveVersionWithFieldLibraryEnumShape', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = input.toString();
      if (url.endsWith('/application-forms/active')) return json(activeRuntimeForm());
      return json({ content: [] });
    });
    vi.stubGlobal('fetch', fetchMock);

    render(<QuoteIntakeFlow metadataState={metadataState} tenantId="tenant-alpha" />);

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/v1/tenants/tenant-alpha/application-forms/active', expect.objectContaining({ headers: expect.objectContaining({ Accept: 'application/json' }) })));
    await screen.findByText(/Published application form version v-runtime-1 loaded for tenant-alpha/i);

    const runtimeCard = screen.getByRole('heading', { name: /^Application Form$/i }).closest('section') as HTMLElement;
    expect(within(runtimeCard).getByText('Runtime header')).toBeInTheDocument();
    expect(within(runtimeCard).getByRole('combobox', { name: /^Mortgage Type/i })).toBeInTheDocument();
    expect(within(runtimeCard).getByRole('option', { name: /^Conventional$/i })).toBeInTheDocument();
    expect(within(runtimeCard).getByRole('textbox', { name: /^Borrower Last Name/i })).toBeInTheDocument();
    expect(within(runtimeCard).getByLabelText(/^Acquisition date/i)).toHaveAttribute('type', 'date');
    expect(within(runtimeCard).getByRole('spinbutton', { name: /^Desired rate lock period/i })).toHaveAttribute('inputmode', 'numeric');
    expect(within(runtimeCard).getByRole('spinbutton', { name: /^Desired rate lock period/i })).toHaveAttribute('min', '15');
    expect(within(runtimeCard).getByRole('spinbutton', { name: /^Desired rate lock period/i })).toHaveAttribute('max', '90');
    expect(within(runtimeCard).queryByRole('spinbutton', { name: /^Base loan amount/i })).not.toBeInTheDocument();

    fireEvent.change(within(runtimeCard).getByRole('combobox', { name: /^Mortgage Type/i }), { target: { value: 'Conventional' } });

    const baseLoanAmount = within(runtimeCard).getByRole('spinbutton', { name: /^Base loan amount/i });
    expect(baseLoanAmount).toBeInTheDocument();
    expect(baseLoanAmount).toHaveAttribute('min', '100000');
    expect(baseLoanAmount).toHaveAttribute('max', '1500000');
    expect(baseLoanAmount).toHaveAttribute('step', '0.01');
  }, 30000);

  it('validatesFieldLibraryNumericDurationAndStepMetadataLocally', () => {
    const fields = [
      { ...orderedField('baseLoanAmount', false, 'text', 1), valueType: { type: 'number', minimum: '100000', maximum: '1500000', precision: 2 } },
      { ...orderedField('purchasePrice', false, 'text', 2), max: '1500000' },
      { ...orderedField('desiredRateLockPeriod', false, 'text', 3), valueType: { type: 'duration', minimum: '15', maximum: '90', precision: 0 } },
      { ...orderedField('loanToValue', false, 'text', 4), constraints: { min: '0', max: '100', step: '0.25' } },
    ];

    const errors = validateFields(fields, { ...filledIntake(), baseLoanAmount: '99999', purchasePrice: '1500000.01', desiredRateLockPeriod: '30.5', loanToValue: '80.10' });

    expect(errors.baseLoanAmount).toBe('Base Loan Amount must be at least 100000.');
    expect(errors.purchasePrice).toBe('Purchase Price must be no more than 1500000.');
    expect(errors.desiredRateLockPeriod).toBe('Desired Rate Lock Period must use no more than 0 decimal places.');
    expect(errors.loanToValue).toBe('Loan To Value must use increments of 0.25.');
    expect(validateFields(fields, { ...filledIntake(), baseLoanAmount: '100000.25', purchasePrice: '1500000', desiredRateLockPeriod: '30', loanToValue: '80.25' })).toEqual({});
  });

  it('recordsConfigurationBlockerWhenNoPublishedTenantFormExists', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = input.toString();
      if (url.endsWith('/application-forms/active')) return { ok: false, status: 404, json: async () => ({}) } as Response;
      return json({ content: [] });
    });
    vi.stubGlobal('fetch', fetchMock);

    render(<QuoteIntakeFlow metadataState={metadataState} tenantId="tenant-missing" />);

    expect(await screen.findByRole('alert')).toHaveTextContent(/configuration blocker: no published tenant application form version is configured/i);
  }, 30000);

  it('showsUnavailableProductRowStatusWithoutCardAffordances', () => {
    render(<QuoteIntakeFlow metadataState={metadataState} />);

    const row = screen.getByRole('row', { name: /Jumbo Preview Product INACTIVE/i });
    expect(within(row).getByText(/Inactive product - row actions unavailable/i)).toBeInTheDocument();
    expect(within(row).getByRole('button', { name: /^Request lock$/i })).toBeDisabled();
    fireEvent.click(within(row).getByRole('button', { name: /^Status$/i }));

    expect(screen.getByText(/Jumbo Preview Product status: Inactive/i)).toBeInTheDocument();
    expect(document.querySelector('.quote-product-card')).not.toBeInTheDocument();
  });
});

function field(fieldId: keyof BorrowerIntake, required: boolean, dataType: ScenarioIntakeField['dataType'] = 'text', overrides: Partial<ScenarioIntakeField> = {}): ScenarioIntakeField {
  return { fieldId, label: String(fieldId).replace(/[A-Z]/g, ' $&').replace(/^./, (c) => c.toUpperCase()), groupId: 'test', dataType, required, helpText: `${fieldId} help`, sourceRef: 'metadata', decisionQuality: 'VERIFIED', validationMessages: [], ...overrides };
}

function orderedField(fieldId: keyof BorrowerIntake, required: boolean, dataType: ScenarioIntakeField['dataType'], displayOrder: number): ScenarioIntakeField {
  return field(fieldId, required, dataType, { displayOrder });
}

function headerField(fieldId: string, label: string, displayOrder: number): ScenarioIntakeField {
  return field(fieldId as keyof BorrowerIntake, false, 'text', { label, displayOrder, valueType: 'header', helpText: `${label} section header` });
}

function filledIntake(): BorrowerIntake {
  return Object.fromEntries(Object.keys(initialQuoteIntake).map((key) => [key, key === 'contactEmail' ? 'alex@example.test' : '1'])) as BorrowerIntake;
}

function activeRuntimeForm() {
  return {
    tenantId: 'tenant-alpha',
    versionId: 'v-runtime-1',
    fieldGroups: [
      {
        groupId: 'scenario-identity',
        label: 'Loan Basics',
        helpText: 'Runtime published tenant form',
        fields: [
          headerField('field@runtime-header', 'Runtime header', 1),
          { ...orderedField('mortgageType', true, 'text', 2), valueType: { type: 'enum', enumTypeId: 'mortgage-type', variants: [{ variantId: '', name: 'Select mortgage type' }, { variantId: 'Conventional', name: 'Conventional' }] } },
          orderedField('borrowerLastName', true, 'string', 3),
          orderedField('acquisitionDate', false, 'date', 4),
          { ...orderedField('desiredRateLockPeriod', false, 'duration', 5), constraints: { minimum: '15', maximum: '90', precision: '0' } },
          { ...orderedField('baseLoanAmount', true, 'number', 6), constraints: { minimum: '100000', maximum: '1500000', precision: '2' }, visibilityCondition: { parentFieldId: 'mortgageType', operator: 'equals', values: ['Conventional'] } },
        ],
      },
    ],
  };
}

function json(body: unknown) {
  return { ok: true, status: 200, json: async () => body } as Response;
}
