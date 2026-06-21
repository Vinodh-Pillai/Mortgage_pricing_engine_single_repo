import '@testing-library/jest-dom/vitest';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { QuoteIntakeFlow, initialQuoteIntake } from './QuoteIntakeFlow';
import type { BorrowerIntake, MetadataState, ScenarioIntakeField, ScenarioIntakeMetadata } from '../../lib/api/quoteRuns';
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
      losPrefillMappings: [
        { fieldId: 'borrowerLastName', sourceSystem: 'LoanPASS', losFieldLabel: 'Borrower last name', losFieldKey: 'quoteBorrowerInfo.borrowerLastName', wcpeField: 'borrowerLastName', confidence: 'MAPPED', lastSync: 'pending configured LOS adapter', missingCategory: 'required_to_price', scope: 'tenant:ui-preview-tenant;channel:configured-metadata', authorizationState: 'tenant quote-runs metadata endpoint', affectedOutputTraces: ['quote-fact:borrowerLastName'] },
        { fieldId: 'decisionCreditScore', sourceSystem: 'LoanPASS', losFieldLabel: 'Credit score', losFieldKey: 'creditScore', wcpeField: 'decisionCreditScore', confidence: 'MAPPED', lastSync: 'pending configured LOS adapter', missingCategory: 'improves_pricing', scope: 'tenant:ui-preview-tenant;channel:configured-metadata', authorizationState: 'tenant quote-runs metadata endpoint', affectedOutputTraces: ['quote-fact:decisionCreditScore'] },
        { fieldId: 'mortgageType', sourceSystem: 'LoanPASS', losFieldLabel: 'Mortgage type', losFieldKey: 'mortgageType', wcpeField: 'mortgageType', confidence: 'UNKNOWN', lastSync: 'pending configured LOS adapter', missingCategory: 'required_before_lock', scope: 'tenant:ui-preview-tenant;channel:configured-metadata', authorizationState: 'tenant quote-runs metadata endpoint', affectedOutputTraces: ['quote-fact:mortgageType'] },
      ],
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

const traceConfiguredMetadataState: MetadataState = {
  kind: 'loaded',
  metadata: {
    ...metadataState.metadata,
    settings: {
      ...(metadataState.metadata.settings ?? {}),
      quoteEconomicsTraceRefs: [
        { productCode: 'CONF_30YR_PREVIEW', metric: 'price', value: 'configured-pricing-trace' },
        { productCode: 'CONF_30YR_PREVIEW', metric: 'fees', value: 'configured-fee-trace' },
        { productCode: 'CONF_30YR_PREVIEW', metric: 'llpa', value: 'configured-llpa-trace' },
      ],
    } as ScenarioIntakeMetadata['settings'] & Record<string, unknown>,
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
    ...(calculationOutputMetadataState as { kind: 'loaded'; metadata: ScenarioIntakeMetadata }).metadata,
    settings: {
      ...((calculationOutputMetadataState as { kind: 'loaded'; metadata: ScenarioIntakeMetadata }).metadata.settings ?? {}),
      fieldPermissions: {
        hiddenFields: ['calc@ltv'],
      },
    },
  },
};

const lockingConfiguredMetadataState: MetadataState = {
  kind: 'loaded',
  metadata: {
    ...(metadataState as { kind: 'loaded'; metadata: ScenarioIntakeMetadata }).metadata,
    settings: {
      ...((metadataState as { kind: 'loaded'; metadata: ScenarioIntakeMetadata }).metadata.settings ?? {}),
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

const baseMetadata = (metadataState as { kind: 'loaded'; metadata: ScenarioIntakeMetadata }).metadata;

const orderedFormMetadataState: MetadataState = {
  kind: 'loaded',
  metadata: {
    ...baseMetadata,
    quickQuoteState: {
      ...baseMetadata.quickQuoteState!,
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
    ...baseMetadata,
    fieldGroups: baseMetadata.fieldGroups.map((group) => ({
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
    ...baseMetadata,
    fieldGroups: baseMetadata.fieldGroups.map((group) => ({
      ...group,
      fields: group.fields.map((candidate) => candidate.fieldId === 'baseLoanAmount'
        ? { ...candidate, required: true, visibilityCondition: { parentFieldId: 'mortgageType', operator: 'equals', values: ['Conventional'] } }
        : candidate),
    })),
  },
};

const multiRequiredValidationBannerMetadataState: MetadataState = {
  kind: 'loaded',
  metadata: {
    ...baseMetadata,
    fieldGroups: baseMetadata.fieldGroups.map((group) => group.groupId === 'loan-structure'
      ? { ...group, fields: [field('loanPurpose', true), field('baseLoanAmount', true, 'number'), field('desiredLoanTerm', false, 'number'), field('desiredAmortizationType', false)] }
      : group),
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
  }, 60000);

  it('keepsSaveRetrieveIdentifiersOutOfTheIntakeFormAndPreferencesStepIsRemoved', () => {
    const { container } = render(<QuoteIntakeFlow metadataState={metadataState} />);
    expect(container.querySelector('#keys-heading')).not.toBeInTheDocument();
    expect(container.querySelector('input[name="borrowerLastName"]')).not.toBeInTheDocument();
    expect(container.querySelector('input[name="loanNumber"]')).not.toBeInTheDocument();
    expect(screen.getAllByRole('combobox', { name: /^Mortgage type/i })[0]).not.toBeRequired();
    expect(screen.queryByRole('button', { name: /Preferences & Launch/i })).not.toBeInTheDocument();
  }, 30000);

  it('keepsPricingFieldEditsLocalUntilExplicitPipelineSave', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = input.toString();
      if (url.endsWith('/scenarios') && init?.method === 'POST') return json({ scenarioId: 'scenario-1', scenarioVersion: 1 });
      if (url.endsWith('/scenario-identity') && init?.method === 'PATCH') return json({ scenarioId: 'scenario-1', scenarioVersion: 2 });
      return json({});
    });
    vi.stubGlobal('fetch', fetchMock);
    render(<QuoteIntakeFlow metadataState={metadataState} />);

    const quoteSection = screen.getByRole('heading', { name: /^Quote$/i }).closest('section') as HTMLElement;
    const mortgageType = within(quoteSection).getByRole('combobox', { name: /^Mortgage type/i });
    fireEvent.change(mortgageType, { target: { value: 'Conventional' } });
    fireEvent.blur(mortgageType);
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/v1/tenants/ui-preview-tenant/application-forms/active', expect.anything()));
    expect(fetchMock.mock.calls.some(([input, init]) => input.toString().endsWith('/scenarios') && init?.method === 'POST')).toBe(false);
    expect(window.localStorage.getItem('wcpe:quoteIntakeDraft:last')).toBeNull();
  }, 60000);

  it('launchesQuoteFromProductFinderWithMinimumStartKeys', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = input.toString();
      if (url.endsWith('/scenarios') && init?.method === 'POST') return json({ scenarioId: 'scenario-launch', scenarioVersion: 1 });
      if (url.endsWith('/quote-runs') && init?.method === 'POST') return json({ status: 'CREATED', runId: 'run-1', nextRoute: '/quote/run-1/offers', validationSummary: { passed: true, status: 'PASSED', message: 'ok', blockers: {} }, uiTraceId: 'trace-quote-launch', events: [], fallbackMode: false, dependencyStatus: '', auditPackageId: 'audit-quote-launch', replayHashRef: 'replay-quote-launch', validationIssues: [] });
      return json({ scenarioId: 'scenario-launch', scenarioVersion: 2, passed: true, status: 'PASSED', message: 'ok', blockers: {} });
    });
    const onNavigate = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    render(<QuoteIntakeFlow metadataState={metadataState} intake={{ ...filledIntake(), borrowerLastName: 'Alex', loanNumber: 'LP-1001', mortgageType: 'Conventional', zip: '90001', contactEmail: 'alex@example.test', state: 'CA' }} onNavigate={onNavigate} />);

    expect(screen.getByRole('heading', { name: /^Products$/i })).toBeInTheDocument();
    fireEvent.click(screen.getAllByRole('button', { name: /^Use for quote$/i })[0]);
    const launchButton = screen.getByRole('button', { name: /^Launch Quote$/i });
    await waitFor(() => expect(launchButton).not.toBeDisabled());
    fireEvent.click(launchButton);
    const launchCall = await waitFor(() => {
      const call = fetchMock.mock.calls.find(([input, init]) => input.toString().endsWith('/quote-runs') && init?.method === 'POST' && init.body);
      expect(call).toBeDefined();
      return call;
    });
    expect(JSON.parse(String(launchCall?.[1]?.body))).toMatchObject({ scenarioId: 'scenario-launch', scenarioVersion: 1, channelCode: 'RETAIL', investorCode: 'FNMA', mortgageType: 'CONVENTIONAL' });
    expect(await screen.findByRole('status', { name: /Pipeline quote launch details/i })).toHaveTextContent(/Conventional 30-Year Preview \(CONF_30YR_PREVIEW\)/i);
    expect(screen.getByRole('status', { name: /Pipeline quote launch details/i })).toHaveTextContent(/trace-quote-launch/i);
    expect(screen.getByRole('status', { name: /Pipeline quote launch details/i })).toHaveTextContent(/audit-quote-launch/i);
    await waitFor(() => expect(onNavigate).toHaveBeenCalledWith('/quote/run-1/offers'));
  }, 60000);

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

  it('keepsSaveIdentifierValidationInsideTheSaveModal', async () => {
    render(<QuoteIntakeFlow metadataState={metadataState} errors={{ loanNumber: 'Loan number is required.' }} />);

    expect(screen.queryByRole('textbox', { name: /^Loan number/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /^Save Pipeline$/i }));
    const dialog = screen.getByRole('dialog', { name: /^Save Pipeline$/i });
    expect(within(dialog).getByRole('textbox', { name: /^Borrower Last Name/i })).toBeRequired();
    expect(within(dialog).getByRole('textbox', { name: /^Loan Number/i })).toBeRequired();

    fireEvent.click(within(dialog).getByRole('button', { name: /^Save$/i }));
    expect(within(dialog).getByRole('alert')).toHaveTextContent(/Borrower Last Name is required to save this pipeline\./i);
    await waitFor(() => expect(within(dialog).getByRole('textbox', { name: /^Borrower Last Name/i })).toHaveFocus());
  }, 30000);

  it('shows concise required indicators and disables launch until the quote is ready', () => {
    render(<QuoteIntakeFlow metadataState={metadataState} />);

    expect(screen.getAllByText('Required').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Required fields').length).toBeGreaterThan(0);
    const launchButton = screen.getByRole('button', { name: /^Launch Quote$/i });
    expect(launchButton).toBeDisabled();
    expect(screen.getByText('Select a product and complete required fields')).toBeInTheDocument();
  });

  it('rendersOperationalNoTextPipelineWithStateChipsInsteadOfHelpCopy', () => {
    const { container } = render(<QuoteIntakeFlow metadataState={metadataState} />);

    expect(container.querySelector('.quote-form-builder__copy')).not.toBeInTheDocument();
    expect(screen.queryByText(/Capture the borrower and loan facts/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/Headers separate runtime sections/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/Configure field-library data types/i)).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('row', { name: /Conventional 30-Year Preview ACTIVE/i }));
    expect(container.querySelector('[aria-label="Required field count"]')).toHaveTextContent(/field missing/i);
    expect(container.querySelector('[aria-label="Application form builder states"]')).not.toBeInTheDocument();
    expect(container.querySelector('[aria-label="Field editor states"]')).not.toBeInTheDocument();
  }, 30000);

  it('rendersFullScreenQuickQuoteShellWithStructuredStatusAndNoPipelineRedirectCopy', () => {
    render(<QuoteIntakeFlow mode="quickquote" metadataState={metadataState} />);

    expect(screen.getByRole('heading', { name: /^QuickQuote$/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /^QuickQuote$/i })).toHaveAttribute('aria-current', 'page');
    const statusStrip = screen.getByRole('region', { name: /QuickQuote status strip/i });
    expect(statusStrip).toBeInTheDocument();
    expect(screen.getAllByText('Prefill').length).toBeGreaterThan(0);
    expect(within(statusStrip).getByText('Missing')).toBeInTheDocument();
    expect(within(statusStrip).getByText('Eligible')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^Save QuickQuote draft$/i })).toBeInTheDocument();
    expect(screen.getByRole('table', { name: /QuickQuote product eligibility grid/i })).toBeInTheDocument();
    expect(screen.getByText(/no QuickQuote product filter is applied/i)).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: /^Review$/i })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: /^Pipeline$/i })).not.toBeInTheDocument();
    expect(screen.queryByText(/Capture the borrower and loan facts/i)).not.toBeInTheDocument();
  }, 30000);

  it('promptsBeforeModeSwitchWithUnsavedQuickQuoteSelectionAndSeedsPipeline', () => {
    const onNavigate = vi.fn();
    const capture = vi.fn();
    render(<QuoteIntakeFlow mode="quickquote" metadataState={metadataState} onNavigate={onNavigate} onEvidenceCapture={capture} />);

    fireEvent.click(screen.getByRole('row', { name: /Conventional 30-Year Preview ACTIVE/i }));
    fireEvent.click(screen.getByRole('link', { name: /^Pipeline$/i }));

    expect(screen.getByRole('dialog', { name: /Switch to Pipeline/i })).toBeInTheDocument();
    expect(capture).toHaveBeenCalledWith(expect.objectContaining({ action: 'quote-mode-switch-confirmation-required', storyId: 'PII-77-S05', mode: 'pipeline', selectedProductCode: 'CONF_30YR_PREVIEW' }));

    fireEvent.click(screen.getByRole('button', { name: /^Discard$/i }));

    expect(onNavigate).toHaveBeenCalledWith('/pipeline?product=CONF_30YR_PREVIEW');
    expect(capture).toHaveBeenCalledWith(expect.objectContaining({ action: 'quote-mode-switched', storyId: 'PII-77-S05', mode: 'pipeline', decision: 'discard' }));
  }, 30000);

  it('keepsAllQuickQuoteProductStatusesVisibleWithReviewTraceRefs', () => {
    const capture = vi.fn();
    render(<QuoteIntakeFlow mode="quickquote" metadataState={metadataState} onEvidenceCapture={capture} />);

    expect(within(screen.getByRole('table', { name: /QuickQuote product eligibility grid/i })).getAllByRole('row')).toHaveLength(6);
    const referableRow = screen.getByRole('row', { name: /FHA 30-Year Preview PENDING/i });
    const ineligibleRow = screen.getByRole('row', { name: /Jumbo Preview Product INACTIVE/i });

    expect(within(referableRow).getByText(/Referable product - review reason available/i)).toBeInTheDocument();
    expect(within(ineligibleRow).getByText(/Inactive product - row actions unavailable/i)).toBeInTheDocument();

    fireEvent.click(referableRow);
    expect(capture).toHaveBeenCalledWith(expect.objectContaining({ action: 'pipeline-row-selected', storyId: 'PII-77-S02' }));
    const auditDetails = screen.getByLabelText(/Product review details/i);
    expect(auditDetails).toHaveTextContent(/Referable state is visible/i);
    expect(auditDetails).toHaveTextContent(/Eligibility/);
    expect(auditDetails).toHaveTextContent(/Calculation/);
    expect(auditDetails).toHaveTextContent(/Adjustment/);
    expect(auditDetails).toHaveTextContent(/Margin/);
    expect(auditDetails).toHaveTextContent(/Pricing/);

    fireEvent.click(within(ineligibleRow).getByRole('button', { name: /^Review status$/i }));
    expect(capture).toHaveBeenCalledWith(expect.objectContaining({ action: 'pipeline-row-status-reviewed', storyId: 'PII-77-S02' }));
    expect(screen.getByText(/Jumbo Preview Product status: Ineligible/i)).toBeInTheDocument();
    expect(screen.getByText(/pricing-service:JUMBO_PREVIEW:rate-output-pending/i)).toBeInTheDocument();
  }, 30000);

  it('exposesQuickQuoteAccessiblePrefillRailGridAndActionsInFocusOrder', async () => {
    const losPrefilledIntake: BorrowerIntake = {
      ...initialQuoteIntake,
      borrowerLastName: 'Rivera',
      loanNumber: 'LN-2001',
      mortgageType: 'Conventional',
      decisionCreditScore: '720',
      actorId: 'loan-officer-1',
    };
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = input.toString();
      if (url.endsWith('/scenarios') && init?.method === 'POST') return json({ scenarioId: 'scenario-los-override-1', scenarioVersion: 1 });
      return json({});
    });
    vi.stubGlobal('fetch', fetchMock);
    const { container, unmount } = render(<QuoteIntakeFlow mode="quickquote" metadataState={metadataState} />);

    const header = container.querySelector('.quickquote-header');
    const prefillRail = screen.getByLabelText(/LOS prefill rail/i);
    const grid = screen.getByRole('table', { name: /QuickQuote product eligibility grid/i });
    const actions = screen.getByLabelText(/QuickQuote actions/i);

    expect(header).toBeInTheDocument();
    expect(prefillRail.compareDocumentPosition(grid) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(grid.compareDocumentPosition(actions) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(screen.getByLabelText(/Borrower and loan context/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/Missing fact reason categories/i)).toBeInTheDocument();
    expect(prefillRail).not.toHaveTextContent(/Source LoanPASS/i);
    expect(prefillRail).not.toHaveTextContent(/WCPE borrowerLastName/i);
    expect(prefillRail).toHaveTextContent(/Required to price: 1/i);
    expect(prefillRail).toHaveTextContent(/Improves pricing: 1/i);
    expect(prefillRail).toHaveTextContent(/Before lock: 1/i);
    expect(screen.getByRole('heading', { name: /Recent edits/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /Borrower details/i })).toBeInTheDocument();

    unmount();
    const overrideRender = render(<QuoteIntakeFlow mode="quickquote" metadataState={metadataState} intake={losPrefilledIntake} />);

    const decisionCreditScore = screen.getByRole('spinbutton', { name: /Decision credit score/i });
    fireEvent.change(decisionCreditScore, { target: { value: '735' } });
    await waitFor(() => expect(decisionCreditScore).toHaveValue(735));
    const overrideAudit = screen.getByRole('heading', { name: /Recent edits/i }).closest('section') as HTMLElement;
    expect(overrideAudit).toHaveTextContent(/decisionCreditScore/i);
    expect(overrideAudit).toHaveTextContent(/Original value: 720/i);
    expect(overrideAudit).toHaveTextContent(/Edited value: 735/i);
    expect(overrideAudit).toHaveTextContent(/Edited by: loan-officer-1 at/i);
    expect(overrideAudit).toHaveTextContent(/Pricing impact is rechecked/i);

    fireEvent.click(screen.getByRole('row', { name: /Conventional 30-Year Preview ACTIVE/i }));
    fireEvent.click(screen.getByRole('button', { name: /^Save QuickQuote draft$/i }));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/v1/tenants/ui-preview-tenant/scenarios', expect.objectContaining({ method: 'POST' })));
    const persistCall = fetchMock.mock.calls.find(([input, init]) => input.toString().endsWith('/scenarios') && init?.method === 'POST');
    const persistedBody = JSON.parse(String(persistCall?.[1]?.body));
    expect(persistedBody).toMatchObject({ data: { borrowerLastName: 'Rivera', loanNumber: 'LN-2001', decisionCreditScore: '735' } });
    const persistedContext = JSON.parse(String(persistedBody.data.clientContext));
    expect(persistedContext.quickQuoteDraft).toMatchObject({
      storyId: 'PII-77-S07',
      sourceMode: 'quickquote',
      selectedProduct: { productCode: 'CONF_30YR_PREVIEW' },
    });
    expect(persistedContext.quickQuoteDraft.comparedProducts.length).toBeGreaterThan(1);
    expect(persistedContext.quickQuoteDraft.quoteOutputs).toEqual(expect.arrayContaining([expect.objectContaining({ productCode: 'CONF_30YR_PREVIEW', metric: 'rate' })]));
    expect(persistedContext.quickQuoteDraft.visibleAssumptions.length).toBeGreaterThan(0);
    expect(persistedContext.quickQuoteDraft.traceReferences).toEqual(expect.arrayContaining([expect.objectContaining({ value: expect.stringContaining('CONF_30YR_PREVIEW') })]));
    expect(persistedContext.losOverrides).toEqual([
      expect.objectContaining({
        fieldId: 'decisionCreditScore',
        label: 'decisionCreditScore',
        originalValue: '720',
        overrideValue: '735',
        actor: 'loan-officer-1',
        affectedOutputTraces: ['quote-fact:decisionCreditScore'],
      }),
    ]);

    overrideRender.unmount();
    render(<QuoteIntakeFlow mode="quickquote" metadataState={metadataState} intake={{ ...losPrefilledIntake, decisionCreditScore: '735', clientContext: persistedBody.data.clientContext }} />);
    const restoredAudit = screen.getByRole('heading', { name: /Recent edits/i }).closest('section') as HTMLElement;
    expect(restoredAudit).toHaveTextContent(/Original value: 720/i);
    expect(restoredAudit).toHaveTextContent(/Edited value: 735/i);
    expect(restoredAudit).toHaveTextContent(/Pricing impact is rechecked/i);
    expect(screen.getAllByRole('row').length).toBeGreaterThan(1);
  }, 30000);

  it('rendersResponsiveQuickQuoteComparisonAndSavesSelectedComparisonProducts', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = input.toString();
      if (url.endsWith('/scenarios') && init?.method === 'POST') return json({ scenarioId: 'scenario-comparison-1', scenarioVersion: 1 });
      return json({});
    });
    vi.stubGlobal('fetch', fetchMock);
    render(<QuoteIntakeFlow mode="quickquote" metadataState={traceConfiguredMetadataState} intake={{ ...initialQuoteIntake, borrowerLastName: 'Rivera', loanNumber: 'LN-2001', mortgageType: 'Conventional' }} />);

    const comparison = screen.getByRole('table', { name: /QuickQuote product comparison/i });
    expect(within(comparison).getByRole('row', { name: /Rate comparison/i })).toBeInTheDocument();
    expect(within(comparison).getByRole('row', { name: /Price comparison/i })).toBeInTheDocument();
    expect(within(comparison).getByRole('row', { name: /Payment comparison/i })).toBeInTheDocument();
    expect(within(comparison).getByRole('row', { name: /DSCR comparison/i })).toBeInTheDocument();
    expect(within(comparison).getByRole('row', { name: /Fees comparison/i })).toBeInTheDocument();
    expect(within(comparison).getByRole('row', { name: /LLPA comparison/i })).toBeInTheDocument();
    expect(within(comparison).getByRole('row', { name: /Assumptions comparison/i })).toHaveTextContent(/No visible assumptions|Referable or missing-data/i);
    expect(within(comparison).getByRole('row', { name: /Trace availability comparison/i })).toHaveTextContent(/trace|pricing-service/i);
    expect(screen.getByLabelText(/Swipe QuickQuote comparison products/i)).toBeInTheDocument();

    fireEvent.click(screen.getByRole('row', { name: /Conventional 30-Year Preview ACTIVE/i }));
    const jumboRow = screen.getByRole('row', { name: /Jumbo Preview Product INACTIVE/i });
    fireEvent.click(within(jumboRow).getByRole('button', { name: /^Add to comparison$/i }));
    fireEvent.click(screen.getByRole('button', { name: /^Save QuickQuote draft from comparison$/i }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/v1/tenants/ui-preview-tenant/scenarios', expect.objectContaining({ method: 'POST' })));
    const persistCall = fetchMock.mock.calls.find(([input, init]) => input.toString().endsWith('/scenarios') && init?.method === 'POST');
    const persistedBody = JSON.parse(String(persistCall?.[1]?.body));
    const persistedContext = JSON.parse(String(persistedBody.data.clientContext));
    const comparedCodes = persistedContext.quickQuoteDraft.comparedProducts.map((product: { productCode: string }) => product.productCode);
    expect(persistedContext.quickQuoteDraft.selectedProduct).toMatchObject({ productCode: 'CONF_30YR_PREVIEW' });
    expect(comparedCodes).toEqual(expect.arrayContaining(['CONF_30YR_PREVIEW', 'JUMBO_PREVIEW']));
    expect(comparedCodes.length).toBeGreaterThanOrEqual(2);
    expect(persistedContext.quickQuoteDraft.visibleAssumptions.length).toBeGreaterThan(0);
  }, 30000);

  it('showsDataRequiredStateFromConfiguredRequiredFieldsAndMovesToNextField', async () => {
    const firstMissing = render(<QuoteIntakeFlow metadataState={metadataState} />);
    expect(screen.queryByRole('heading', { name: /^Complete required fields$/i })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('row', { name: /Conventional 30-Year Preview ACTIVE/i }));

    const dataRequired = screen.getByRole('heading', { name: /^Complete required fields$/i }).closest('section') as HTMLElement;
    const requiredFieldIds = dataRequired.getAttribute('data-required-field-ids') ?? '';
    expect(dataRequired).toHaveAttribute('data-required-field-ids', expect.stringContaining('mortgageType'));
    expect(requiredFieldIds).not.toContain('borrowerLastName');
    expect(requiredFieldIds).not.toContain('loanNumber');

    fireEvent.click(within(dataRequired).getByRole('button', { name: /^Next Field$/i }));
    expect(dataRequired.getAttribute('data-required-field-ids')).toBe('mortgageType');
    firstMissing.unmount();

    render(<QuoteIntakeFlow metadataState={metadataState} intake={{ ...initialQuoteIntake, mortgageType: 'Conventional' }} />);
    fireEvent.click(screen.getByRole('row', { name: /Conventional 30-Year Preview ACTIVE/i }));
    expect(screen.queryByRole('heading', { name: /^Complete required fields$/i })).not.toBeInTheDocument();
  }, 30000);

  it('showsClosableStickyValidationBannerForOneInvalidFieldAndEnablesLaunchAfterFix', () => {
    vi.stubGlobal('fetch', vi.fn(async () => json({})));
    const { container } = render(<QuoteIntakeFlow metadataState={metadataState} />);
    const firstProductRow = container.querySelector('.quote-product-row:not(.quote-product-row--header)') as HTMLElement;

    fireEvent.click(firstProductRow);

    const banner = container.querySelector('.quote-validation-top-banner') as HTMLElement;
    expect(banner).toHaveTextContent(/Mortgage Type needs attention/i);
    expect(banner).toHaveClass('quote-validation-top-banner');
    expect(banner).not.toHaveTextContent(/more fields need attention/i);

    fireEvent.click(banner.querySelector('.quote-validation-top-banner__close') as HTMLButtonElement);
    expect(container.querySelector('.quote-validation-top-banner')).not.toBeInTheDocument();

    fireEvent.click(Array.from(firstProductRow.querySelectorAll('button')).find((button) => button.textContent === 'Use for quote') as HTMLButtonElement);
    expect(container.querySelector('.quote-validation-top-banner')).not.toBeInTheDocument();
    const launchButton = Array.from(container.querySelectorAll('button')).find((button) => button.textContent === 'Launch Quote') as HTMLButtonElement;
    expect(launchButton).not.toBeDisabled();
  }, 30000);

  it('summarizesMultipleInvalidFieldsAndRedHighlightsVisibleControls', async () => {
    const { container } = render(<QuoteIntakeFlow metadataState={multiRequiredValidationBannerMetadataState} />);

    fireEvent.click(screen.getByRole('row', { name: /Conventional 30-Year Preview ACTIVE/i }));

    expect(screen.getByRole('alert', { name: /Mortgage Type needs attention/i })).toHaveTextContent(/2 more fields need attention/i);
    const invalidMortgageType = container.querySelector('#mortgageType') as HTMLSelectElement;
    await waitFor(() => expect(invalidMortgageType).toHaveFocus());
    expect(invalidMortgageType).toHaveAttribute('aria-invalid', 'true');
    expect(container.querySelector('#loanPurpose')).toHaveAttribute('aria-invalid', 'true');
    expect(container.querySelector('#baseLoanAmount')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.queryByText(/is required before pricing refresh/i)).not.toBeInTheDocument();
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

    fireEvent.click(screen.getByRole('button', { name: /^Save Pipeline$/i }));
    const saveDialog = screen.getByRole('dialog', { name: /^Save Pipeline$/i });
    fireEvent.change(within(saveDialog).getByLabelText(/Borrower Last Name/i), { target: { value: 'Rivera' } });
    fireEvent.change(within(saveDialog).getByLabelText(/Loan Number/i), { target: { value: 'LN-2001' } });
    fireEvent.click(within(saveDialog).getByRole('button', { name: /^Save$/i }));
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
    expect(screen.getByText(/Retrieved scenario-pipeline-1/i)).toBeInTheDocument();
  }, 60000);

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
    const { container } = render(<QuoteIntakeFlow metadataState={metadataState} />);
    const filters = container.querySelector('#filters-heading')?.closest('section') as HTMLElement;
    const [mortgageTypeFilter, , , statusFilter] = Array.from(filters.querySelectorAll('select')) as HTMLSelectElement[];

    expect(container.querySelector('.quote-intake-data-required')).not.toBeInTheDocument();
    expect(screen.getByText(/5 filtered of 5 total · 3 active · 1 pending · 1 inactive/i)).toBeInTheDocument();
    expect(container.querySelector('.quote-product-row[aria-label="VA 30-Year Preview ACTIVE"]')).toBeInTheDocument();
    fireEvent.change(mortgageTypeFilter, { target: { value: 'FHA' } });
    expect(screen.getByText(/1 filtered of 5 total · 0 active · 1 pending · 0 inactive/i)).toBeInTheDocument();
    expect(container.querySelector('.quote-product-row[aria-label="VA 30-Year Preview ACTIVE"]')).not.toBeInTheDocument();
    expect(container.querySelector('.quote-product-row[aria-label="FHA 30-Year Preview PENDING"]')).toBeInTheDocument();
    fireEvent.change(statusFilter, { target: { value: 'ACTIVE' } });
    expect(screen.getByText(/0 filtered of 5 total · 0 active · 0 pending · 0 inactive/i)).toBeInTheDocument();
    expect(screen.getByText(/No matches/i)).toBeInTheDocument();
    fireEvent.change(statusFilter, { target: { value: 'PENDING' } });
    expect(screen.getByText(/1 filtered of 5 total · 0 active · 1 pending · 0 inactive/i)).toBeInTheDocument();
    expect(container.querySelector('.quote-product-row[aria-label="FHA 30-Year Preview PENDING"]')).toBeInTheDocument();
  }, 30000);

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

  it('rendersReusableQuoteEconomicsMetricsWithConfiguredOutputsAndTraceLinks', () => {
    const { container } = render(<QuoteIntakeFlow mode="quickquote" metadataState={traceConfiguredMetadataState} intake={{ ...filledIntake(), secondaryAdjustment: '101.250', totalLiabilityMonthlyPayment: '$2,450', estimatedDSCR: '1.23x', 'calc@fees': '$1,100 fees', 'calc@llpa': '0.250 LLPA' } as BorrowerIntake & Record<string, string>} />);

    const row = screen.getByRole('row', { name: /Conventional 30-Year Preview ACTIVE/i });
    expect(within(row).getByText('101.250')).toBeInTheDocument();
    expect(within(row).getByText('$2,450')).toBeInTheDocument();
    expect(within(row).getByText('1.23x')).toBeInTheDocument();
    expect(within(row).getByText('$1,100 fees')).toBeInTheDocument();
    expect(within(row).getByText('0.250 LLPA')).toBeInTheDocument();
    fireEvent.click(row);
    const pricingTrace = within(row).getByRole('link', { name: /Pricing trace/i });
    const feeTrace = within(row).getByRole('link', { name: /Fee trace/i });
    const llpaTrace = within(row).getByRole('link', { name: /LLPA trace/i });
    expect(pricingTrace).toHaveAttribute('href', '#quote-trace-configured-pricing-trace');
    expect(pricingTrace).not.toHaveAttribute('href', expect.stringContaining('pricing-service'));
    expect(feeTrace).toHaveAttribute('href', '#quote-trace-configured-fee-trace');
    expect(llpaTrace).toHaveAttribute('href', '#quote-trace-configured-llpa-trace');
    expect(container.querySelector('#quote-trace-configured-pricing-trace')).toHaveTextContent('configured-pricing-trace');
    expect(container.querySelector('#quote-trace-configured-fee-trace')).toHaveTextContent('configured-fee-trace');
    expect(container.querySelector('#quote-trace-configured-llpa-trace')).toHaveTextContent('configured-llpa-trace');
  });

  it('showsExplicitMissingQuoteEconomicsStatesTiedToMissingFacts', () => {
    render(<QuoteIntakeFlow mode="quickquote" metadataState={metadataState} />);

    const row = screen.getByRole('row', { name: /Conventional 30-Year Preview ACTIVE/i });
    expect(row.querySelector('[aria-label="Payment: Missing data"]')).toBeInTheDocument();
    expect(row.querySelector('[aria-label="DSCR: Missing data"]')).toBeInTheDocument();
    expect(within(row).getAllByText(/Needs Mortgage Type/i).length).toBeGreaterThan(0);
    expect(within(row).queryByRole('link', { name: /trace/i })).not.toBeInTheDocument();
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
    fireEvent.keyDown(row, { key: 'Enter' });
    expect(row).toHaveAttribute('aria-selected', 'true');
    fireEvent.click(within(row).getByRole('button', { name: /^Use for quote$/i }));

    expect(row).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByText(/Conventional 30-Year Preview selected/i)).toBeInTheDocument();
    expect(onNavigate).not.toHaveBeenCalled();
  });

  it('bindsLockRequestFieldsFromMetadataAndRequiresApprovalWhenAutoConfirmationDisabled', () => {
    render(<QuoteIntakeFlow metadataState={lockingConfiguredMetadataState} intake={{ ...filledIntake(), effectiveDate: '2026-06-18', requestedLockPeriodDays: '30', lockExtension: '5' }} />);

    const row = screen.getByRole('row', { name: /Conventional 30-Year Preview ACTIVE/i });
    fireEvent.click(within(row).getByRole('button', { name: /^Request lock$/i }));

    expect(screen.getByText(/Approval required before lock, reprice, relock, or cancellation/i)).toBeInTheDocument();
    expect(screen.getByText(/Approval path: lock-desk-review/i)).toBeInTheDocument();
    expect(screen.getByText('Request date: 2026-06-18')).toBeInTheDocument();
    expect(screen.getByText('Expiration date: 30')).toBeInTheDocument();
    expect(screen.getByText('Extension: 5')).toBeInTheDocument();
  });

  it('rendersRuntimeApplicationFormSectionsHeadersAndOmitsRemovedFieldsWithoutBuilderControls', () => {
    const { container } = render(<QuoteIntakeFlow metadataState={orderedFormMetadataState} />);

    expect(screen.queryByRole('heading', { name: /^Borrower Details$/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: /^Property$/i })).not.toBeInTheDocument();
    expect(container.querySelector('[data-field-id="occupancyType"]')).not.toBeInTheDocument();
    expect(container.querySelector('.quote-form-builder')).not.toBeInTheDocument();
  });

  it('doesNotExposeApplicationFormBuilderDraftOrderingOnUserScreens', () => {
    const { container } = render(<QuoteIntakeFlow metadataState={orderedFormMetadataState} tenantId="tenant-alpha" />);

    expect(container.querySelector('.quote-form-builder')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^Save form draft order$/i })).not.toBeInTheDocument();
    expect(window.localStorage.getItem('wcpe:application-form-builder:tenantalpha:draft-order')).toBeNull();
    expect(window.localStorage.getItem('wcpe:application-form-builder:tenantbeta:draft-order')).toBeNull();
  });

  it('doesNotExposeConditionalVisibilityAuthoringOnUserScreens', () => {
    render(<QuoteIntakeFlow metadataState={orderedFormMetadataState} tenantId="tenant-alpha" />);

    expect(screen.queryByLabelText(/Parent field/i)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/Condition value/i)).not.toBeInTheDocument();
    expect(window.localStorage.getItem('wcpe:application-form-builder:tenantalpha:conditional-visibility')).toBeNull();
  }, 30000);

  it('ignoresLocalBuilderDraftsWhenRenderingUserRuntimeFields', () => {
    window.localStorage.setItem('wcpe:application-form-builder:tenantalpha:conditional-visibility', JSON.stringify({ tenantId: 'tenant-alpha', conditions: { baseLoanAmount: { parentFieldId: 'occupancyType', operator: 'equals', values: ['Investment'] } } }));
    render(<QuoteIntakeFlow metadataState={orderedFormMetadataState} tenantId="tenant-alpha" />);

    expect(screen.queryByText(/references hidden or removed parent field occupancyType/i)).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^Save form draft order$/i })).not.toBeInTheDocument();
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
    fireEvent.click(screen.getByRole('row', { name: /Conventional 30-Year Preview ACTIVE/i }));
    const hiddenDataRequired = screen.getByRole('heading', { name: /^Complete required fields$/i }).closest('section') as HTMLElement;
    expect(hiddenDataRequired.getAttribute('data-required-field-ids')).toBe('mortgageType');
    expect(hiddenDataRequired.getAttribute('data-required-field-ids')).not.toContain('baseLoanAmount');
    hidden.unmount();

    render(<QuoteIntakeFlow metadataState={conditionalRequiredMetadataState} intake={{ ...filledIntake(), mortgageType: 'Conventional', baseLoanAmount: '100000' }} />);
    fireEvent.click(screen.getByRole('row', { name: /Conventional 30-Year Preview ACTIVE/i }));
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
    await screen.findByText(/Application fields loaded/i);

    const runtimeCard = screen.getByRole('heading', { name: /^Borrower Details$/i }).closest('section') as HTMLElement;
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

    expect(await screen.findByRole('alert')).toHaveTextContent(/Application fields are unavailable\. Setup support is needed\./i);
  }, 30000);

  it('showsUnavailableProductRowStatusWithoutCardAffordances', () => {
    render(<QuoteIntakeFlow metadataState={metadataState} />);

    const row = screen.getByRole('row', { name: /Jumbo Preview Product INACTIVE/i });
    expect(within(row).getByText(/Inactive product - row actions unavailable/i)).toBeInTheDocument();
    expect(within(row).getByRole('button', { name: /^Request lock$/i })).toBeDisabled();
    fireEvent.click(within(row).getByRole('button', { name: /^Review status$/i }));

    expect(screen.getByText(/Jumbo Preview Product status: Ineligible/i)).toBeInTheDocument();
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
