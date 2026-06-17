import { type FocusEvent, type FormEvent, type ReactNode, useEffect, useMemo, useRef, useState } from 'react';
import { loanPassQuoteIntakeFields, type BorrowerIntake, type LaunchState, type MetadataState, type ScenarioIntakeField } from '../../lib/api/quoteRuns';
import { availableFiltersFor, tenantHomePreviewProducts, type AuthorizedProduct, type TenantProductStatus } from '../../lib/api/tenantHome';
import { createDraftScenario, getDraftScenario, loadDraftBackup, saveDraftBackup, updateDraftScenario, type DraftBackup, type DraftScenario } from './draft';
import { launchQuoteRun } from './launch';
import { fieldsForStep, normalizeMetadataState, quoteIntakeSteps, type QuoteIntakeStepDefinition } from './metadata';
import { ResumeDraft, draftToBackup } from './ResumeDraft';
import { StepFields } from './steps/StepFields';
import { errorsToValidation, firstInvalidField, validateFields, type IntakeFieldErrors } from './validation';
import './QuoteIntake.css';

export type PipelineIntakePageProps = {
  tenantId?: string;
  intake?: BorrowerIntake;
  errors?: IntakeFieldErrors;
  launchState?: LaunchState;
  metadataState: MetadataState;
  onChange?: (field: keyof BorrowerIntake, value: string) => void;
  onRetry?: () => void;
  onSubmit?: (event: FormEvent<HTMLFormElement>) => void;
  onNavigate?: (route: string) => void;
  onEvidenceCapture?: (event: Record<string, unknown>) => void;
};

type ProductFilterState = {
  productType: string;
  investor: string;
  channel: string;
  status: string;
  rateMin: string;
  rateMax: string;
  ltvMin: string;
  ltvMax: string;
  ficoMin: string;
  ficoMax: string;
  loanAmountMin: string;
  loanAmountMax: string;
  term: string;
  propertyType: string;
  occupancy: string;
};

const tenantBoundaryPlaceholder = 'ui-preview-tenant';
const localUnsyncedDraftId = 'local-unsynced-pipeline-draft';
const minimumStartFields: Array<keyof BorrowerIntake> = ['borrowerLastName', 'loanNumber'];
const minimumQuoteFields: Array<keyof BorrowerIntake> = ['borrowerLastName', 'loanNumber', 'mortgageType', 'channel', 'loanPurpose', 'decisionCreditScore', 'baseLoanAmount'];
const emptyProductFilters: ProductFilterState = {
  productType: '',
  investor: '',
  channel: '',
  status: '',
  rateMin: '',
  rateMax: '',
  ltvMin: '',
  ltvMax: '',
  ficoMin: '',
  ficoMax: '',
  loanAmountMin: '',
  loanAmountMax: '',
  term: '',
  propertyType: '',
  occupancy: '',
};

const initialQuoteIntakeDefaults: Partial<Record<keyof BorrowerIntake, string>> = {
  channel: '',
  loanNumber: '',
  borrowerFirstName: '',
  borrowerLastName: '',
  numberOfBorrowers: '',
  contactEmail: '',
  coBorrowerName: '',
  coBorrowerRole: '',
  decisionCreditScore: '',
  creditScoreType: '',
  citizenshipType: '',
  professional: '',
  firstTimeHomeBuyer: '',
  firstTimeInvestor: '',
  loanPurpose: '',
  baseLoanAmount: '',
  purchasePrice: '',
  appraisedValue: '',
  downPayment: '',
  percentDownpayment: '',
  loanToValue: '',
  combinedLoanToValue: '',
  cashOutAmount: '',
  loanBalance: '',
  firstLoanBalance: '',
  secondLoanBalance: '',
  firstLienAmount: '',
  secondLienAmount: '',
  lienPosition: '',
  refinancingType: '',
  desiredLoanTerm: '',
  desiredAmortizationType: '',
  desiredRateLockPeriod: '',
  lockPeriodType: '',
  desiredInterestRate: '',
  prepaymentPenaltyTerm: '',
  prePaymentPenaltyStructureType: '',
  waiveEscrows: '',
  interestOnly: '',
  mortgageType: '',
  loanQualificationType: '',
  mortgageInsuranceType: '',
  miOptionType: '',
  gift: '',
  achPayment: '',
  wholesaleCompensation: '',
  compensationPaidType: '',
  vaLoanType: '',
  vaFundingFeeExemptionType: '',
  vaFirstTimeUse: '',
  state: '',
  zip: '',
  countyFips: '',
  countyCode: '',
  countyName: '',
  city: '',
  street: '',
  addressSearchString: '',
  propertyType: '',
  occupancyType: '',
  numberOfUnits: '',
  propertyLocation: '',
  numberOfLeasedUnits: '',
  shortTermRental: '',
  propertySquareFootage: '',
  propertyAcreageNumber: '',
  condoApprovalType: '',
  acquisitionDate: '',
  monthlyMarketRent: '',
  propertyRentalIncome: '',
  rentalIncomeMightBeUsed: '',
  investorExperience: '',
  additionalMonthlyHousingExpenses: '',
  additionalAnnualHousingExpenses: '',
  monthlyTaxes: '',
  monthlyInsurance: '',
  monthlyHOA: '',
  annualTaxes: '',
  annualInsurance: '',
  annualHOA: '',
  selfEmployed: '',
  selfEmployedTimeFrame: '',
  totalBorrowerIncome: '',
  monthlyDebt: '',
  totalLiabilityMonthlyPayment: '',
  estimatedDti: '',
  estimatedDSCR: '',
  monthsOfReserves: '',
  liquidAssets: '',
  documentationType: '',
  secondaryDocumentationType: '',
  documentationTypeTimeFrame: '',
  mortgageLatePayments: '',
  mortgageHistoryDesc: '',
  creditEvent: '',
  chapter7BankruptcyDate: '',
  chapter11BankruptcyDate: '',
  chapter13BankruptcyDate: '',
  deedInLieuDate: '',
  foreclosureDate: '',
  shortSaleDate: '',
  mortgageModificationDate: '',
  forbearanceDate: '',
  lockExtension: '',
  lockExtension2: '',
  concession: '',
  secondaryAdjustment: '',
  aus: '',
  manualUnderwriting: '',
  quoteIntent: '',
  scenarioName: '',
  externalLoanId: '',
  sourceSystem: '',
  borrowerName: '',
  borrowerRole: '',
  creditStatus: '',
  creditScore: '',
  creditScoreSource: '',
  creditReportDate: '',
  creditReadiness: '',
  loanAmount: '',
  purchasePriceOrValue: '',
  propertyZip: '',
  termMonths: '',
  amortizationType: '',
  requestedLockPeriodDays: '',
  propertyState: '',
  propertyCounty: '',
  unitCount: '',
  condoProjectType: '',
  manufacturedHomeFlag: '',
  monthlyIncome: '',
  incomeType: '',
  employmentType: '',
  suppliedDti: '',
  reserveMonths: '',
  incomeVerificationStatus: '',
  assetVerificationStatus: '',
  productFamily: '',
  productPreference: '',
  quoteFilters: '',
  effectiveDate: '',
  actorId: '',
  clientContext: '',
  downPaymentOrEquity: '',
  subordinateFinancingAmount: '',
  helocDrawnAmount: '',
  helocLimitAmount: '',
  reserves: '',
};

export const initialQuoteIntake = Object.fromEntries(
  loanPassQuoteIntakeFields.map((field) => [field, initialQuoteIntakeDefaults[field] ?? '']),
) as BorrowerIntake;

export function PipelineIntakePage({
  tenantId = tenantBoundaryPlaceholder,
  intake,
  errors = {},
  launchState,
  metadataState,
  onChange,
  onRetry,
  onSubmit,
  onNavigate,
  onEvidenceCapture,
}: PipelineIntakePageProps) {
  const [localIntake, setLocalIntake] = useState<BorrowerIntake>(intake ?? initialQuoteIntake);
  const [scenarioId, setScenarioId] = useState<string | null>(null);
  const [scenarioVersion, setScenarioVersion] = useState(0);
  const [localErrors, setLocalErrors] = useState<IntakeFieldErrors>({});
  const [flowState, setFlowState] = useState<LaunchState>(launchState ?? { kind: 'idle' });
  const [resumeBackup, setResumeBackup] = useState<DraftBackup | null>(() => loadDraftBackup(draftIdFromLocation() ?? undefined));
  const [resumeDismissed, setResumeDismissed] = useState(false);
  const [resumeLoading, setResumeLoading] = useState(false);
  const [resumeError, setResumeError] = useState('');
  const [statusMessage, setStatusMessage] = useState('');
  const [submitAttempted, setSubmitAttempted] = useState(false);
  const [productFilters, setProductFilters] = useState<ProductFilterState>(emptyProductFilters);
  const [selectedProduct, setSelectedProduct] = useState<AuthorizedProduct | null>(null);
  const [retrieveOpen, setRetrieveOpen] = useState(false);
  const [retrieveKeys, setRetrieveKeys] = useState({ borrowerLastName: '', loanNumber: '' });
  const [retrieveError, setRetrieveError] = useState('');
  const [retrieveLoading, setRetrieveLoading] = useState(false);
  const savingRef = useRef(false);
  const savePromiseRef = useRef<Promise<DraftScenario | null> | null>(null);
  const firstEntryDraftRef = useRef(false);
  const values = useMemo(() => normalizeDisplayedIntake(intake ?? localIntake), [intake, localIntake]);
  const mergedErrors = { ...errors, ...localErrors };
  const { metadata } = normalizeMetadataState(metadataState);
  const compatibilityStartFieldIds = useMemo(() => startFieldIdsForMetadata(metadata, values), [metadata, values]);
  const showProgressiveCompatibility = compatibilityStartFieldIds.some((fieldId) => fieldId === 'quoteIntent');
  const sections = useMemo(() => consolidateCanonicalFields(quoteIntakeSteps.map((step) => ({ step, fields: fieldsForStep(metadata, step.id).map(normalizePipelineRequirement) }))), [metadata]);
  const visibleErrors = useMemo(() => (submitAttempted || showProgressiveCompatibility ? mergedErrors : {}), [mergedErrors, showProgressiveCompatibility, submitAttempted]);
  const draftId = draftIdFromLocation();
  const availableFilters = useMemo(() => availableFiltersFor(tenantHomePreviewProducts), []);
  const activeFilterCount = Object.values(productFilters).filter((value) => value.trim()).length;
  const filteredProducts = useMemo(() => filterProducts(tenantHomePreviewProducts, productFilters), [productFilters]);
  const startReady = minimumStartFields.every((field) => (values[field] ?? '').trim());
  const quoteReady = minimumQuoteFields.every((field) => (values[field] ?? '').trim()) && Boolean(selectedProduct);
  const completionPercent = completedFactPercent(values);

  useEffect(() => {
    if (intake) setLocalIntake(intake);
  }, [intake]);

  useEffect(() => {
    if (!draftId || resumeBackup || resumeDismissed) return;
    let cancelled = false;
    setResumeLoading(true);
    getDraftScenario(tenantId, draftId)
      .then((draft) => { if (!cancelled) setResumeBackup(draftToBackup(draft)); })
      .catch((error: unknown) => { if (!cancelled) setResumeError(error instanceof Error ? error.message : 'Draft scenario could not be loaded.'); })
      .finally(() => { if (!cancelled) setResumeLoading(false); });
    return () => { cancelled = true; };
  }, [draftId, resumeBackup, resumeDismissed, tenantId]);

  useEffect(() => {
    const timer = window.setInterval(() => {
      if (hasAnyValue(values)) void saveDraft('autosave-interval');
    }, 30_000);
    return () => window.clearInterval(timer);
  }, [values, scenarioId, scenarioVersion]);

  function changeField(field: keyof BorrowerIntake, value: string) {
    const nextValues = { ...values, [field]: value };
    setLocalIntake(nextValues);
    setLocalErrors((current) => ({ ...current, [field]: undefined }));
    onChange?.(field, value);
    if (!firstEntryDraftRef.current && isLoanBasicsField(field) && value.trim()) {
      firstEntryDraftRef.current = true;
      void saveDraft('first-field-entry', nextValues);
    }
  }

  function setFilter(key: keyof ProductFilterState, value: string) {
    setProductFilters((current) => ({ ...current, [key]: value }));
    if (key === 'productType') changeField('mortgageType', value ? productTypeLabel(value) : '');
    if (key === 'channel') changeField('channel', value ? channelLabel(value) : '');
    if (key === 'ltvMin' || key === 'ltvMax') changeField('loanToValue', value);
    if (key === 'ficoMin' || key === 'ficoMax') changeField('decisionCreditScore', value);
    if (key === 'loanAmountMin' || key === 'loanAmountMax') changeField('baseLoanAmount', value);
    if (key === 'term') changeField('desiredLoanTerm', value);
    if (key === 'propertyType') changeField('propertyType', value);
    if (key === 'occupancy') changeField('occupancyType', value);
  }

  function applyProduct(product: AuthorizedProduct) {
    const channel = channelLabel(product.channelCode);
    const mortgageType = productTypeLabel(product.productType);
    changeField('channel', channel);
    changeField('mortgageType', mortgageType);
    setSelectedProduct(product);
    setProductFilters((current) => ({ ...current, productType: product.productType, investor: product.investorCode, channel: product.channelCode, status: product.status }));
    setStatusMessage(`${product.productName} selected.`);
    capture('pipeline-product-selected', { productCode: product.productCode, investor: product.investorCode, channel });
  }

  function clearFilters() {
    setProductFilters({ ...emptyProductFilters, status: '' });
    setStatusMessage('Filters cleared.');
  }

  async function savePipeline() {
    setSubmitAttempted(true);
    const validationErrors = validateFields(startFields(), values);
    if (Object.keys(validationErrors).length > 0) {
      setLocalErrors(validationErrors);
      setFlowState({ kind: 'blocked', validation: errorsToValidation(validationErrors, 'Borrower Last Name and Loan Number are required.') });
      focusFirstInvalid(validationErrors);
      return;
    }
    let saved = await saveDraft('manual-save');
    if (!saved) return;
    try {
      const loanBasics = sectionForStep(1);
      saved = await updateDraftScenario(tenantId, saved.scenarioId, saved.scenarioVersion, 'scenario-identity', backendDraftPayload(values, loanBasics?.fields ?? []));
      setScenarioId(saved.scenarioId);
      setScenarioVersion(saved.scenarioVersion);
      saveDraftBackup(saved.scenarioId, saved.scenarioVersion, 1, values);
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : 'Draft scenario save is temporarily unavailable.';
      setFlowState({ kind: 'outage', message });
      setStatusMessage(message);
      capture('pipeline-save-failed', { message });
      return;
    }
    savePipelineLookup(values, saved.scenarioId, saved.scenarioVersion);
    setStatusMessage('Saved.');
  }

  async function retrievePipeline() {
    const borrowerLastName = retrieveKeys.borrowerLastName.trim();
    const loanNumber = retrieveKeys.loanNumber.trim();
    if (!borrowerLastName || !loanNumber) {
      setRetrieveError('Borrower Last Name and Loan Number are required.');
      return;
    }
    setRetrieveError('');
    setRetrieveLoading(true);
    try {
      const draft = await getDraftScenario(tenantId, { borrowerLastName, loanNumber });
      applyRetrievedDraft(draft, `Retrieved ${draft.scenarioId}.`);
    } catch (error: unknown) {
      const backup = loadPipelineLookup(borrowerLastName, loanNumber);
      if (!backup) {
        setRetrieveError(error instanceof Error ? error.message : 'No saved pipeline found.');
        return;
      }
      try {
        const draft = await getDraftScenario(tenantId, backup.scenarioId);
        applyRetrievedDraft({ ...draft, intake: draft.intake ?? backup.intake }, `Retrieved ${draft.scenarioId}.`);
      } catch {
        applyRetrievedDraft(backup, 'Retrieved from local backup; backend draft refresh was unavailable.');
      }
    } finally {
      setRetrieveLoading(false);
    }
  }

  function applyRetrievedDraft(draft: DraftScenario, message: string) {
    setScenarioId(draft.scenarioId);
    setScenarioVersion(draft.scenarioVersion);
    const resumedIntake = normalizeDisplayedIntake({ ...values, ...(draft.intake ?? {}) });
    setLocalIntake(resumedIntake);
    Object.entries(resumedIntake).forEach(([field, value]) => {
      if (typeof value === 'string') onChange?.(field as keyof BorrowerIntake, value);
    });
    setRetrieveOpen(false);
    setRetrieveError('');
    setStatusMessage(message);
    capture('pipeline-retrieved', { scenarioId: draft.scenarioId, scenarioVersion: draft.scenarioVersion });
  }

  async function saveDraft(reason = 'manual-save', valueOverride?: BorrowerIntake): Promise<DraftScenario | null> {
    if (savingRef.current) return savePromiseRef.current;
    savingRef.current = true;
    const draftValues = valueOverride ?? values;
    const operation = persistDraft(reason, draftValues);
    savePromiseRef.current = operation;
    try {
      return await operation;
    } finally {
      savingRef.current = false;
      savePromiseRef.current = null;
    }
  }

  async function persistDraft(reason: string, draftValues: BorrowerIntake): Promise<DraftScenario | null> {
    setFlowState({ kind: 'submitting' });
    try {
      const loanBasics = sectionForStep(1);
      if (!scenarioId && !resumeBackup?.scenarioId) {
        const draft = await createDraftScenario(tenantId, backendDraftPayload(draftValues, loanBasics?.fields ?? []));
        setScenarioId(draft.scenarioId);
        setScenarioVersion(draft.scenarioVersion);
        saveDraftBackup(draft.scenarioId, draft.scenarioVersion, 1, draftValues);
        setStatusMessage('Pipeline draft created and saved.');
        if (reason !== 'pre-launch') setFlowState({ kind: 'idle' });
        capture('draft-created', { scenarioId: draft.scenarioId, scenarioVersion: draft.scenarioVersion, reason });
        return draft;
      } else {
        const saved = await updateDraftScenario(tenantId, scenarioId ?? resumeBackup?.scenarioId ?? localUnsyncedDraftId, scenarioVersion || resumeBackup?.scenarioVersion || 1, 'scenario-identity', backendDraftPayload(draftValues, loanBasics?.fields ?? []));
        setScenarioId(saved.scenarioId);
        setScenarioVersion(saved.scenarioVersion);
        saveDraftBackup(saved.scenarioId, saved.scenarioVersion, 1, draftValues);
        setStatusMessage('Pipeline draft auto-saved.');
        if (reason !== 'pre-launch') setFlowState({ kind: 'idle' });
        capture('draft-updated', { scenarioId: saved.scenarioId, scenarioVersion: saved.scenarioVersion, reason });
        return saved;
      }
    } catch (error: unknown) {
      saveDraftBackup(scenarioId ?? resumeBackup?.scenarioId ?? localUnsyncedDraftId, scenarioVersion || resumeBackup?.scenarioVersion || 0, 1, draftValues);
      const message = error instanceof Error ? error.message : 'Draft service is unavailable; local draft backup was stored.';
      setFlowState({ kind: 'outage', message });
      setStatusMessage(`${message} Local backup is available for resume.`);
      capture('draft-local-backup', { reason, message });
      return null;
    }
  }

  async function launch(event?: FormEvent<HTMLFormElement>, product?: AuthorizedProduct) {
    event?.preventDefault();
    if (event) onSubmit?.(event);
    setSubmitAttempted(true);
    if (product) applyProduct(product);
    const launchValues = product ? { ...values, channel: channelLabel(product.channelCode), mortgageType: productTypeLabel(product.productType) } : values;
    const validationErrors = validateFields(quoteFields(), launchValues);
    if (Object.keys(validationErrors).length > 0) {
      setLocalErrors(validationErrors);
      setFlowState({ kind: 'blocked', validation: errorsToValidation(validationErrors, 'Complete required quote fields.') });
      focusFirstInvalid(validationErrors);
      capture('pipeline-start-validation-error', { fields: Object.keys(validationErrors) });
      return;
    }

    setFlowState({ kind: 'submitting' });
    try {
      const savedDraft = await saveDraft('pre-launch', launchValues);
      const resolvedScenarioId = savedDraft?.scenarioId ?? scenarioId ?? resumeBackup?.scenarioId;
      if (!resolvedScenarioId) throw new Error('Connected scenario draft is unavailable; quote launch requires backend draft support.');
      const resolvedVersion = savedDraft?.scenarioVersion ?? (scenarioVersion || resumeBackup?.scenarioVersion || 1);
      const launched = await launchQuoteRun(tenantId, resolvedScenarioId, resolvedVersion, launchValues);
      if (launched.kind === 'blocked') {
        setLocalErrors(launched.validation.blockers);
        setFlowState({ kind: 'blocked', validation: launched.validation });
        capture('quote-launch-blocked', { blockers: launched.blockers });
        return;
      }
      if (launched.kind === 'needs-attention') {
        setFlowState({ kind: 'blocked', validation: { passed: false, status: 'BLOCKED', message: launched.message, blockers: {} } });
        setStatusMessage(launched.message);
        capture('quote-launch-needs-attention', { blockers: launched.blockers });
        return;
      }
      setFlowState({ kind: 'created', launch: launched.launch });
      capture('quote-launch-created', { runId: launched.launch.runId, nextRoute: launched.launch.nextRoute, productCode: product?.productCode });
      if (launched.launch.nextRoute) onNavigate?.(launched.launch.nextRoute);
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : 'Quote launch is temporarily unavailable.';
      setFlowState({ kind: 'outage', message });
      setStatusMessage(message);
      capture('quote-launch-outage', { message });
    }
  }

  function resumeDraft() {
    if (!resumeBackup) return;
    setScenarioId(resumeBackup.scenarioId);
    setScenarioVersion(resumeBackup.scenarioVersion);
    const resumedIntake = normalizeDisplayedIntake({ ...values, ...resumeBackup.intake });
    setLocalIntake(resumedIntake);
    Object.entries(resumedIntake).forEach(([field, value]) => {
      if (typeof value === 'string') onChange?.(field as keyof BorrowerIntake, value);
    });
    setResumeDismissed(true);
    setStatusMessage(`Draft ${resumeBackup.scenarioId} resumed.`);
  }

  function handleBlur(event: FocusEvent<HTMLFormElement>) {
    const target = event.target as unknown as HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement;
    if (target.name && target.value.trim()) void saveDraft('field-blur');
  }

  function sectionForStep(stepId: QuoteIntakeStepDefinition['id']) {
    return sections.find((candidate) => candidate.step.id === stepId);
  }

  function fieldsById(fieldIds: Array<keyof BorrowerIntake>) {
    return fieldIds.map((fieldId) => fieldForId(sections, fieldId)).filter((field): field is ScenarioIntakeField => Boolean(field));
  }

  function startFields() {
    if (showProgressiveCompatibility) return [];
    return compatibilityStartFieldIds.map((fieldId) => {
      const field = fieldForId(sections, fieldId);
      return field ? { ...field, label: minimumStartFieldLabel(fieldId), required: true, dataType: 'text' as const } : fallbackStartField(fieldId);
    });
  }

  function quoteFields() {
    const fields = fieldsById(minimumQuoteFields).map((field) => ({ ...field, required: true }));
    return fields.length > 0 ? fields : startFields();
  }

  function quoteDetailFields() {
    return fieldsById(['loanPurpose', 'documentationType', 'decisionCreditScore', 'baseLoanAmount', 'state', 'zip']).map((field) => ({
      ...field,
      required: minimumQuoteFields.includes(field.fieldId),
    }));
  }

  function capture(action: string, detail: Record<string, unknown>) {
    onEvidenceCapture?.({ action, storyId: 'PII-26-S13', ...detail });
  }

  return (
    <section id="borrower-intake" className="quote-intake-shell quote-intake-shell--product-finder quote-intake-shell--pipeline-redesign quote-intake-shell--single-page" aria-labelledby="intake-heading">
      <div className="quote-pipeline-topbar">
        <div>
          <p className="eyebrow">Pipeline</p>
          {showProgressiveCompatibility ? <h1>New prospect intake</h1> : null}
          <h2 id="intake-heading">Intake</h2>
          {showProgressiveCompatibility ? <p>Capture the borrower and loan facts needed to start a pricing run.</p> : null}
        </div>
        <div className="quote-pipeline-topbar__stats" aria-label="Pipeline status">
          <MetricCard label="Products" value={String(filteredProducts.length)} detail={`${tenantHomePreviewProducts.length} total`} />
          <MetricCard label="Filters" value={String(activeFilterCount)} detail="active" />
          <MetricCard label="Profile" value={`${completionPercent}%`} detail="complete" tone={quoteReady ? 'ready' : startReady ? 'attention' : undefined} />
        </div>
        <div className="quote-pipeline-topbar__actions" aria-label="Pipeline actions">
          <button type="button" onClick={() => void savePipeline()}>Save Pipeline</button>
          <button type="button" onClick={() => setRetrieveOpen(true)}>Retrieve Pipeline</button>
        </div>
      </div>

      <ResumeDraft draftId={draftId} backup={resumeDismissed ? null : resumeBackup} loading={resumeLoading} error={resumeError} onResume={resumeDraft} onDismiss={() => setResumeDismissed(true)} />
      <LaunchBanner state={flowState} onRetry={() => { setFlowState({ kind: 'idle' }); onRetry?.(); }} />
      {showProgressiveCompatibility ? <ProgressiveCompatibilityNav errors={visibleErrors} /> : null}
      {showProgressiveCompatibility ? (
        <div className="quote-intake-fields quote-intake-fields--compatibility" aria-label="Quick quote identity fields">
          <label className="quote-intake-field">Quote intent<input name="quoteIntent" value={values.quoteIntent ?? ''} aria-invalid={Boolean(visibleErrors.quoteIntent)} onChange={(event) => changeField('quoteIntent', event.target.value)} type="text" />{visibleErrors.quoteIntent ? <p className="quote-intake-error" role="alert">{visibleErrors.quoteIntent}</p> : null}</label>
          <label className="quote-intake-field">Channel<input name="channel" value={values.channel ?? ''} aria-invalid={Boolean(visibleErrors.channel)} onChange={(event) => changeField('channel', event.target.value)} type="text" />{visibleErrors.channel ? <p className="quote-intake-error" role="alert">{visibleErrors.channel}</p> : null}</label>
        </div>
      ) : null}
      {statusMessage ? <p className="quote-intake-status" role="status">{statusMessage}</p> : null}

      <form id="pipeline-product-form" className="quote-intake-form quote-intake-form--product-finder quote-intake-form--single-page" onSubmit={(event) => void launch(event)} onBlur={handleBlur} noValidate>
        <aside className="quote-product-filter-panel" aria-label="Pipeline filters">
          <FilterCard title="Keys" eyebrow="Save" badge={startReady ? 'Ready' : '*'}>
            <StepFields fields={startFields()} intake={values} errors={visibleErrors} onChange={changeField} />
          </FilterCard>

          <FilterCard title="Filters" eyebrow="Products" badge={`${activeFilterCount} active`}>
            <SelectFilter label="Mortgage type" value={productFilters.productType} values={availableFilters.productTypes} onChange={(value) => setFilter('productType', value)} formatter={productTypeLabel} />
            <SelectFilter label="Investor" value={productFilters.investor} values={availableFilters.investors} onChange={(value) => setFilter('investor', value)} />
            <SelectFilter label="Channel" value={productFilters.channel} values={availableFilters.channels} onChange={(value) => setFilter('channel', value)} formatter={channelLabel} />
            <SelectFilter label="Status" value={productFilters.status} values={['ACTIVE', 'PENDING', 'INACTIVE']} onChange={(value) => setFilter('status', value)} />
            <RangeFilter label="Rate" min={productFilters.rateMin} max={productFilters.rateMax} onMin={(value) => setFilter('rateMin', value)} onMax={(value) => setFilter('rateMax', value)} suffix="%" />
            <RangeFilter label="LTV" min={productFilters.ltvMin} max={productFilters.ltvMax} onMin={(value) => setFilter('ltvMin', value)} onMax={(value) => setFilter('ltvMax', value)} suffix="%" />
            <RangeFilter label="FICO" min={productFilters.ficoMin} max={productFilters.ficoMax} onMin={(value) => setFilter('ficoMin', value)} onMax={(value) => setFilter('ficoMax', value)} />
            <RangeFilter label="Loan amount" min={productFilters.loanAmountMin} max={productFilters.loanAmountMax} onMin={(value) => setFilter('loanAmountMin', value)} onMax={(value) => setFilter('loanAmountMax', value)} prefix="$" />
            <CompactField label="Term" value={productFilters.term} onChange={(value) => setFilter('term', value)} placeholder="Months" inputMode="numeric" />
            <CompactField label="Property type" value={productFilters.propertyType} onChange={(value) => setFilter('propertyType', value)} />
            <CompactField label="Occupancy" value={productFilters.occupancy} onChange={(value) => setFilter('occupancy', value)} />
            <button type="button" className="quote-filter-clear" onClick={clearFilters}>Clear</button>
          </FilterCard>

          <FilterCard title="Quote" eyebrow="Launch" badge={quoteReady ? 'Ready' : '*'}>
            <StepFields fields={quoteDetailFields()} intake={values} errors={visibleErrors} onChange={changeField} />
          </FilterCard>
        </aside>

        <section className="quote-product-results-panel" aria-labelledby="product-results-heading">
          <div className="quote-results-toolbar">
            <div>
              <p className="eyebrow">Grid</p>
              <h3 id="product-results-heading">Products</h3>
            </div>
            <div className="quote-results-toolbar__actions">
              <span className="quote-live-indicator"><span aria-hidden="true" />Live</span>
            </div>
          </div>

          {filteredProducts.length === 0 ? (
            <div className="quote-product-empty" role="status">
              <strong>No matches.</strong>
            </div>
          ) : null}

          <div className="quote-product-grid" role="list" aria-label="Filtered mortgage products">
            {filteredProducts.map((product) => (
              <ProductResultCard key={product.productCode} product={product} values={values} selected={selectedProduct?.productCode === product.productCode} onSelect={setSelectedProduct} onApply={applyProduct} />
            ))}
          </div>
        </section>

        <div className="quote-pipeline-launchbar">
          <div>
            <strong>{selectedProduct?.productName ?? 'No product selected'}</strong>
            <span>{quoteReady ? 'Ready' : 'Complete * fields'}</span>
          </div>
          <button type="submit" className="quote-intake-primary" disabled={flowState.kind === 'submitting'}>{flowState.kind === 'submitting' ? 'Launching...' : 'Launch Quote'}</button>
        </div>
      </form>

      {selectedProduct ? <ProductDetailPanel product={selectedProduct} values={values} onBack={() => setSelectedProduct(null)} onUse={applyProduct} /> : null}
      {retrieveOpen ? (
        <div className="quote-pipeline-modal" role="dialog" aria-modal="true" aria-labelledby="retrieve-pipeline-heading">
          <div className="quote-pipeline-modal__card">
            <div className="quote-pipeline-modal__header">
              <h3 id="retrieve-pipeline-heading">Retrieve Pipeline</h3>
              <button type="button" onClick={() => setRetrieveOpen(false)} aria-label="Close">×</button>
            </div>
            <CompactField label="Borrower Last Name" value={retrieveKeys.borrowerLastName} onChange={(value) => setRetrieveKeys((current) => ({ ...current, borrowerLastName: value }))} required />
            <CompactField label="Loan Number" value={retrieveKeys.loanNumber} onChange={(value) => setRetrieveKeys((current) => ({ ...current, loanNumber: value }))} required />
            {retrieveError ? <p className="quote-intake-error" role="alert">{retrieveError}</p> : null}
            <div className="quote-pipeline-modal__actions">
              <button type="button" className="quote-filter-clear" onClick={() => setRetrieveOpen(false)}>Back</button>
              <button type="button" className="quote-intake-primary" onClick={() => void retrievePipeline()} disabled={retrieveLoading}>{retrieveLoading ? 'Retrieving...' : 'Retrieve'}</button>
            </div>
          </div>
        </div>
      ) : null}
    </section>
  );
}

function FilterCard({ title, eyebrow, badge, children }: { title: string; eyebrow: string; badge: string; children: ReactNode }) {
  return (
    <section className="quote-filter-card" aria-labelledby={`${slug(title)}-heading`}>
      <div className="quote-filter-card__heading">
        <div><p className="eyebrow">{eyebrow}</p><h3 id={`${slug(title)}-heading`}>{title}</h3></div>
        <span>{badge}</span>
      </div>
      {children}
    </section>
  );
}

function SelectFilter({ label, values, value, onChange, formatter = (candidate) => candidate }: { label: string; values: string[]; value: string; onChange: (value: string) => void; formatter?: (value: string) => string }) {
  return (
    <label className="quote-compact-filter-row">
      <span>{label}</span>
      <select value={value} onChange={(event) => onChange(event.target.value)}>
        <option value="">All</option>
        {values.map((candidate) => <option key={candidate} value={candidate}>{formatter(candidate)}</option>)}
      </select>
    </label>
  );
}

function CompactField({ label, value, onChange, placeholder, required, inputMode }: { label: string; value: string; onChange: (value: string) => void; placeholder?: string; required?: boolean; inputMode?: 'numeric' | 'decimal' }) {
  return (
    <label className="quote-compact-filter-row">
      <span>{label}{required ? <em aria-hidden="true"> *</em> : null}</span>
      <input value={value} placeholder={placeholder} inputMode={inputMode} onChange={(event) => onChange(event.target.value)} required={required} />
    </label>
  );
}

function RangeFilter({ label, min, max, onMin, onMax, prefix = '', suffix = '' }: { label: string; min: string; max: string; onMin: (value: string) => void; onMax: (value: string) => void; prefix?: string; suffix?: string }) {
  return (
    <div className="quote-compact-filter-row quote-compact-filter-row--range">
      <span>{label}</span>
      <div>
        <input value={min} aria-label={`${label} minimum`} inputMode="decimal" placeholder={`${prefix}Min${suffix}`} onChange={(event) => onMin(event.target.value)} />
        <input value={max} aria-label={`${label} maximum`} inputMode="decimal" placeholder={`${prefix}Max${suffix}`} onChange={(event) => onMax(event.target.value)} />
      </div>
    </div>
  );
}

function ProductResultCard({ product, values, selected, onSelect, onApply }: { product: AuthorizedProduct; values: BorrowerIntake; selected: boolean; onSelect: (product: AuthorizedProduct) => void; onApply: (product: AuthorizedProduct) => void }) {
  const fit = productFitScore(product, values);
  return (
    <article className="quote-product-card quote-product-card--compact" role="listitem" aria-label={`${product.productName} ${product.status}`} data-selected={selected} onClick={() => onSelect(product)} tabIndex={0} onKeyDown={(event) => { if (event.key === 'Enter' || event.key === ' ') onSelect(product); }}>
      <div className="quote-product-card__glow" aria-hidden="true" />
      <div className="quote-product-card__header">
        <div>
          <p>{product.productCode}</p>
          <h4>{product.productName}</h4>
        </div>
        <span className={`quote-status-pill quote-status-pill--${statusClass(product.status)}`}>{product.status}</span>
      </div>
      <div className="quote-rate-meter" aria-label={`Rate indicator ${rateIndicator(product)}`}>
        <span style={{ inlineSize: `${Math.max(26, fit)}%` }} />
      </div>
      <dl className="quote-product-card__facts">
        <div><dt>Investor</dt><dd>{product.investorCode}</dd></div>
        <div><dt>Rate</dt><dd>{rateIndicator(product)}</dd></div>
        <div><dt>APR</dt><dd>{aprIndicator(product)}</dd></div>
        <div><dt>LTV</dt><dd>{values.loanToValue || '—'}</dd></div>
        <div><dt>FICO</dt><dd>{values.decisionCreditScore || '—'}</dd></div>
        <div><dt>Term</dt><dd>{values.desiredLoanTerm || values.termMonths || termFromProductName(product.productName)}</dd></div>
      </dl>
      <div className="quote-product-card__actions">
        <button type="button" onClick={(event) => { event.stopPropagation(); onApply(product); }}>Use</button>
      </div>
    </article>
  );
}

function ProductDetailPanel({ product, values, onBack, onUse }: { product: AuthorizedProduct; values: BorrowerIntake; onBack: () => void; onUse: (product: AuthorizedProduct) => void }) {
  return (
    <aside className="quote-product-detail-panel" aria-label="Product detail">
      <div className="quote-product-detail-panel__header">
        <button type="button" onClick={onBack}>← Back</button>
        <span className={`quote-status-pill quote-status-pill--${statusClass(product.status)}`}>{product.status}</span>
      </div>
      <div>
        <p className="eyebrow">{product.productCode}</p>
        <h3>{product.productName}</h3>
      </div>
      <dl className="quote-product-detail-grid">
        <div><dt>Investor</dt><dd>{product.investorCode}</dd></div>
        <div><dt>Mortgage</dt><dd>{productTypeLabel(product.productType)}</dd></div>
        <div><dt>Channel</dt><dd>{channelLabel(product.channelCode)}</dd></div>
        <div><dt>Rate</dt><dd>{rateIndicator(product)}</dd></div>
        <div><dt>APR</dt><dd>{aprIndicator(product)}</dd></div>
        <div><dt>LTV</dt><dd>{values.loanToValue || '—'}</dd></div>
        <div><dt>FICO</dt><dd>{values.decisionCreditScore || '—'}</dd></div>
        <div><dt>Term</dt><dd>{values.desiredLoanTerm || values.termMonths || termFromProductName(product.productName)}</dd></div>
        <div><dt>Loan amount</dt><dd>{values.baseLoanAmount || values.loanAmount || '—'}</dd></div>
        <div><dt>Property type</dt><dd>{values.propertyType || '—'}</dd></div>
        <div><dt>Occupancy</dt><dd>{values.occupancyType || '—'}</dd></div>
        <div><dt>Updated</dt><dd>{product.lastUpdated}</dd></div>
      </dl>
      <button type="button" className="quote-intake-primary" onClick={() => onUse(product)}>Use Product</button>
    </aside>
  );
}

function MetricCard({ label, value, detail, tone }: { label: string; value: string; detail: string; tone?: 'ready' | 'attention' }) {
  return <div className="quote-metric-card" data-tone={tone}><span>{label}</span><strong>{value}</strong><small>{detail}</small></div>;
}

function pickFields(values: BorrowerIntake, fields: ScenarioIntakeField[]) {
  return fields.reduce((acc, field) => {
    acc[field.fieldId] = values[field.fieldId];
    return acc;
  }, {} as Partial<BorrowerIntake>);
}

function backendDraftPayload(values: BorrowerIntake, fields: ScenarioIntakeField[]) {
  const selected = fields.length > 0 ? pickFields(values, fields) : {};
  minimumStartFields.forEach((field) => { selected[field] = values[field]; });
  return Object.fromEntries(Object.entries({ ...values, ...selected }).filter(([, value]) => value.trim())) as Partial<BorrowerIntake>;
}

function isLoanBasicsField(field: keyof BorrowerIntake) {
  return ['borrowerLastName', 'loanNumber'].includes(field);
}

function startFieldIdsForMetadata(metadata: ReturnType<typeof normalizeMetadataState>['metadata'], values: BorrowerIntake): Array<keyof BorrowerIntake> {
  const configuredFields = metadata?.quickQuoteState?.minimalFirstStepFields ?? [];
  const supportedFields = configuredFields.filter((fieldId): fieldId is keyof BorrowerIntake => fieldId in initialQuoteIntakeDefaults);
  const isLegacyQuickQuoteStart = supportedFields.length > 0 && !supportedFields.includes('borrowerLastName') && !supportedFields.includes('loanNumber');
  return isLegacyQuickQuoteStart ? supportedFields : minimumStartFields;
}

function ProgressiveCompatibilityNav({ errors }: { errors: IntakeFieldErrors }) {
  return (
    <nav className="quote-intake-progress" aria-label="Quote intake progress">
      <ol>
        <li><button type="button" aria-current="step">Identity <small>in progress</small></button></li>
        <li><button type="button" disabled>Borrower <small>{errors.quoteIntent ? 'needs attention' : 'empty'}</small></button></li>
        <li><button type="button" disabled>Loan <small>empty</small></button></li>
        <li><button type="button" disabled>Property <small>empty</small></button></li>
        <li><button type="button" disabled>Income <small>empty</small></button></li>
        <li><button type="button" disabled>Launch <small>empty</small></button></li>
      </ol>
    </nav>
  );
}

function normalizeDisplayedIntake(input: BorrowerIntake): BorrowerIntake {
  const normalizedInput = {
    ...input,
    zip: input.zip || input.propertyZip || '',
    downPayment: input.downPayment || input.downPaymentOrEquity || '',
    secondLienAmount: input.secondLienAmount || input.subordinateFinancingAmount || '',
    monthsOfReserves: input.monthsOfReserves || input.reserves || '',
  };
  if (normalizedInput.purchasePrice !== '-1' && normalizedInput.appraisedValue !== '-1') return normalizedInput;
  return {
    ...normalizedInput,
    purchasePrice: normalizedInput.purchasePrice === '-1' ? '' : normalizedInput.purchasePrice,
    appraisedValue: normalizedInput.appraisedValue === '-1' ? '' : normalizedInput.appraisedValue,
  };
}

function consolidateCanonicalFields(sections: Array<{ step: QuoteIntakeStepDefinition; fields: ScenarioIntakeField[] }>) {
  const seenCanonical = new Set<keyof BorrowerIntake>();
  const canonicalSingleSurfaceFields = new Set<keyof BorrowerIntake>(['zip']);
  return sections.map(({ step, fields }) => ({
    step,
    fields: fields.filter((field) => {
      if (!canonicalSingleSurfaceFields.has(field.fieldId)) return true;
      if (seenCanonical.has(field.fieldId)) return false;
      seenCanonical.add(field.fieldId);
      return true;
    }),
  }));
}

function normalizePipelineRequirement(field: ScenarioIntakeField): ScenarioIntakeField {
  return { ...field, required: minimumStartFields.includes(field.fieldId) };
}

function hasAnyValue(values: BorrowerIntake) {
  return Object.values(values).some((value) => value.trim());
}

function fieldForId(sections: Array<{ step: QuoteIntakeStepDefinition; fields: ScenarioIntakeField[] }>, fieldId: keyof BorrowerIntake) {
  return sections.flatMap(({ fields }) => fields).find((field) => field.fieldId === fieldId);
}

function filterProducts(products: AuthorizedProduct[], filters: ProductFilterState) {
  return products.filter((product) => {
    const typeMatches = !filters.productType || filters.productType === product.productType;
    const investorMatches = !filters.investor || filters.investor === product.investorCode;
    const channelMatches = !filters.channel || filters.channel === product.channelCode;
    const statusMatches = !filters.status || filters.status === product.status;
    const rate = product.baseRateMin ?? product.baseRateMax ?? product.rateSpread;
    const minRate = numberFromFilter(filters.rateMin);
    const maxRate = numberFromFilter(filters.rateMax);
    const minRateMatches = minRate == null || rate == null || rate >= minRate;
    const maxRateMatches = maxRate == null || rate == null || rate <= maxRate;
    return typeMatches && investorMatches && channelMatches && statusMatches && minRateMatches && maxRateMatches;
  });
}

function numberFromFilter(value: string) {
  if (!value.trim()) return null;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function completedFactPercent(values: BorrowerIntake) {
  const trackedFields: Array<keyof BorrowerIntake> = ['borrowerLastName', 'loanNumber', 'mortgageType', 'channel', 'loanPurpose', 'documentationType', 'decisionCreditScore', 'baseLoanAmount', 'state', 'zip', 'propertyType', 'occupancyType', 'purchasePrice', 'appraisedValue'];
  const completed = trackedFields.filter((field) => (values[field] ?? '').trim()).length;
  return Math.round((completed / trackedFields.length) * 100);
}

function productFitScore(product: AuthorizedProduct, values: BorrowerIntake) {
  let score = 58;
  if (values.mortgageType && normalized(values.mortgageType) === normalized(product.productType)) score += 16;
  if (values.channel && normalized(values.channel) === normalized(channelLabel(product.channelCode))) score += 10;
  if (values.documentationType) score += 6;
  if (values.propertyType) score += 5;
  if (values.decisionCreditScore) score += 5;
  return Math.min(98, score);
}

function rateIndicator(product: AuthorizedProduct) {
  if (product.baseRateMin != null && product.baseRateMax != null) return `${product.baseRateMin.toFixed(3)}% - ${product.baseRateMax.toFixed(3)}%`;
  if (product.baseRateMin != null || product.baseRateMax != null) return `${(product.baseRateMin ?? product.baseRateMax)?.toFixed(3)}%`;
  if (product.rateSpread != null) return `${product.rateSpread.toFixed(2)} spread`;
  return 'Rate pending';
}

function aprIndicator(_product: AuthorizedProduct) {
  return '—';
}

function termFromProductName(productName: string) {
  const years = productName.match(/(\d{2})\s*-?\s*Year/i)?.[1];
  return years ? `${Number(years) * 12}` : '—';
}

function savePipelineLookup(values: BorrowerIntake, scenarioId: string, scenarioVersion: number) {
  if (typeof window === 'undefined') return;
  const borrowerLastName = values.borrowerLastName.trim();
  const loanNumber = values.loanNumber.trim();
  if (!borrowerLastName || !loanNumber) return;
  const backup: DraftBackup = { scenarioId, scenarioVersion, currentStep: 1, intake: values, savedAt: new Date().toISOString(), status: 'DRAFT_INCOMPLETE' };
  window.localStorage.setItem(pipelineLookupKey(borrowerLastName, loanNumber), JSON.stringify(backup));
}

function loadPipelineLookup(borrowerLastName: string, loanNumber: string) {
  if (typeof window === 'undefined') return null;
  const raw = window.localStorage.getItem(pipelineLookupKey(borrowerLastName, loanNumber));
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as DraftBackup;
    if (!parsed.scenarioId || typeof parsed.scenarioVersion !== 'number') return null;
    return parsed;
  } catch {
    return null;
  }
}

function pipelineLookupKey(borrowerLastName: string, loanNumber: string) {
  return `wcpe:pipeline:${normalized(borrowerLastName)}:${normalized(loanNumber)}`;
}

function minimumStartFieldLabel(fieldId: keyof BorrowerIntake) {
  const labels: Partial<Record<keyof BorrowerIntake, string>> = {
    borrowerLastName: 'Borrower Last Name',
    loanNumber: 'Loan Number',
    mortgageType: 'Mortgage Type',
    quoteIntent: 'Quote intent',
    channel: 'Channel',
  };
  return labels[fieldId] ?? friendlyLabel(fieldId);
}

function fallbackStartField(fieldId: keyof BorrowerIntake): ScenarioIntakeField {
  return {
    fieldId,
    label: minimumStartFieldLabel(fieldId),
    groupId: 'scenario-identity',
    dataType: 'text',
    required: true,
    helpText: '',
    sourceRef: 'pipeline-intake',
    decisionQuality: 'VERIFIED',
    validationMessages: [],
  };
}

function productTypeLabel(value: string) {
  const mapped: Record<string, string> = { CONVENTIONAL: 'Conventional', NONQM: 'NonQM', NON_QM: 'NonQM', FHA: 'FHA', VA: 'VA', JUMBO: 'Jumbo', HOME_EQUITY: 'Home Equity' };
  return mapped[value.toUpperCase()] ?? friendlyLabel(value);
}

function channelLabel(value: string) {
  const mapped: Record<string, string> = { RETAIL: 'Retail', WHOLESALE: 'Wholesale', CORR: 'Correspondent', CORRESPONDENT: 'Correspondent', TPO: 'TPO', CONSUMER_DIRECT: 'Consumer Direct' };
  return mapped[value.toUpperCase()] ?? friendlyLabel(value);
}

function friendlyLabel(value: string) {
  return value.toLowerCase().split(/[_\s-]+/).filter(Boolean).map((part) => part[0].toUpperCase() + part.slice(1)).join(' ');
}

function normalized(value: string) {
  return value.toLowerCase().replace(/[^a-z0-9]/g, '');
}

function statusClass(status: TenantProductStatus) {
  if (status === 'ACTIVE') return 'ready';
  if (status === 'PENDING') return 'attention';
  return 'blocked';
}

function slug(value: string) {
  return value.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
}

function draftIdFromLocation() {
  if (typeof window === 'undefined') return null;
  const params = new URLSearchParams(window.location.search);
  return params.get('draft') ?? params.get('scenarioId');
}

function focusFirstInvalid(nextErrors: IntakeFieldErrors) {
  const invalid = firstInvalidField(nextErrors);
  if (invalid) window.setTimeout(() => document.getElementById(invalid)?.focus(), 0);
}

function LaunchBanner({ state, onRetry }: { state: LaunchState; onRetry: () => void }) {
  if (state.kind === 'idle') return null;
  if (state.kind === 'submitting') return <p className="quote-intake-banner quote-intake-banner--info" role="status">Saving pipeline intake...</p>;
  if (state.kind === 'outage') {
    return <div className="quote-intake-banner quote-intake-banner--blocked" role="alert"><strong>Pipeline service fallback</strong><span>{state.message}</span><button type="button" onClick={onRetry}>Retry</button></div>;
  }
  if (state.kind === 'blocked') {
    return <div className="quote-intake-banner quote-intake-banner--blocked" role="alert"><strong>Pipeline intake blocked</strong><span>{state.validation.message}</span></div>;
  }
  return <div className="quote-intake-banner quote-intake-banner--success" role="status"><strong>Pipeline run created</strong><span>Run ID: {state.launch.runId}</span><span>Next step: {state.launch.nextRoute ?? 'Review offers'}</span></div>;
}

export default PipelineIntakePage;
