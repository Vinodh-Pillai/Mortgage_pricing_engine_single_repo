import { type CSSProperties, type FocusEvent, type FormEvent, type ReactNode, useEffect, useMemo, useRef, useState } from 'react';
import { fetchActiveApplicationFormVersion, fetchTenantDropdownOptions, loanPassQuoteIntakeFields, type ApplicationFormRuntimeState, type BorrowerIntake, type DropdownOption, type LaunchState, type MetadataState, type ScenarioIntakeField, type ScenarioIntakeMetadata, type TenantDropdownOptions } from '../../lib/api/quoteRuns';
import { availableFiltersFor, tenantHomePreviewProducts, type AuthorizedProduct, type TenantProductStatus } from '../../lib/api/tenantHome';
import { createDraftScenario, getDraftScenario, loadDraftBackup, saveDraftBackup, updateDraftScenario, type DraftBackup, type DraftScenario } from './draft';
import { emptyFieldEditorDraft, fieldEditorDataTypeLabel, fieldEditorDataTypes, serializeFieldEditorDraft, validateFieldEditorDraft, type FieldEditorDraft, type FieldEditorSerializedDraft } from './fieldEditorDataTypes';
import { launchQuoteRun } from './launch';
import { fieldsForStep, isHeaderMetadataField, normalizeMetadataState, stepsForMetadata, type QuoteIntakeSection, type QuoteIntakeStepDefinition } from './metadata';
import { ResumeDraft, draftToBackup } from './ResumeDraft';
import { StepFields, type DropdownOptionsByField } from './steps/StepFields';
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

type DropdownConfigState =
  | { kind: 'loading'; options: TenantDropdownOptions | null; message: string }
  | { kind: 'ready'; options: TenantDropdownOptions; message: string }
  | { kind: 'fallback'; options: TenantDropdownOptions; message: string };

type BasePriceScenarioColumnKey = 'product' | 'investor' | 'status' | 'adjustedRate' | 'lockPeriod' | 'adjustedPrice' | 'payment' | 'pitiaItia' | 'dscr' | 'ltv' | 'fico' | 'actions';
type PriceScenarioColumnKey = BasePriceScenarioColumnKey | string;

type PriceScenarioColumn = {
  key: PriceScenarioColumnKey;
  label: string;
  fieldId?: string;
  missingLabel?: string;
  blockedLabel?: string;
};

type ProductRowAction = 'refresh' | 'lock' | 'status';

type ProductRowActionState = {
  productCode: string;
  tone: 'ready' | 'attention' | 'blocked';
  message: string;
  details: string[];
};

type LockFieldBinding = {
  label: string;
  configuredFieldId: string;
  value: string;
};

type VisibilityConditionOperator = NonNullable<ScenarioIntakeField['visibilityCondition']>['operator'];

type VisibilityConditionDraft = {
  parentFieldId: keyof BorrowerIntake;
  operator: VisibilityConditionOperator;
  values: string[];
};

type PipelineRequiredField = ScenarioIntakeField & { fieldId: keyof BorrowerIntake; pipelineRequired?: boolean };


const tenantBoundaryPlaceholder = 'ui-preview-tenant';
const localUnsyncedDraftId = 'local-unsynced-pipeline-draft';
const tenantDropdownConfigCache = new Map<string, DropdownConfigState>();
const minimumStartFields: Array<keyof BorrowerIntake> = ['borrowerLastName', 'loanNumber'];
const minimumQuoteFields: Array<keyof BorrowerIntake> = ['borrowerLastName', 'loanNumber', 'mortgageType', 'channel', 'loanPurpose', 'decisionCreditScore', 'baseLoanAmount'];
const defaultPriceScenarioColumns: PriceScenarioColumn[] = [
  { key: 'product', label: 'Product' },
  { key: 'investor', label: 'Investor' },
  { key: 'adjustedRate', label: 'Adjusted rate' },
  { key: 'lockPeriod', label: 'Lock period' },
  { key: 'adjustedPrice', label: 'Adjusted price' },
  { key: 'payment', label: 'Payment' },
  { key: 'pitiaItia', label: 'PITIA/ITIA' },
  { key: 'dscr', label: 'DSCR' },
  { key: 'actions', label: 'Actions' },
];
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
  channelCode: '',
  channelType: '',
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
  transactionType: '',
  loanBalance: '',
  firstLoanBalance: '',
  secondLoanBalance: '',
  firstLienAmount: '',
  secondLienAmount: '',
  lienPosition: '',
  refinancingType: '',
  desiredLoanTerm: '',
  loanTermType: '',
  desiredAmortizationType: '',
  desiredRateLockPeriod: '',
  lockPeriodType: '',
  desiredInterestRate: '',
  prepaymentPenaltyTerm: '',
  prePaymentPenaltyStructureType: '',
  waiveEscrows: '',
  interestOnly: '',
  mortgageType: '',
  investorCode: '',
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
  propertyInformationType: '',
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
  incomeDocumentationType: '',
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
  const [rowActionState, setRowActionState] = useState<ProductRowActionState | null>(null);
  const [dropdownConfigState, setDropdownConfigState] = useState<DropdownConfigState>(() => tenantDropdownConfigCache.get(tenantId) ?? { kind: 'loading', options: fallbackTenantDropdownOptions(), message: 'Loading tenant dropdown options...' });
  const [retrieveOpen, setRetrieveOpen] = useState(false);
  const [retrieveKeys, setRetrieveKeys] = useState({ borrowerLastName: '', loanNumber: '' });
  const [retrieveError, setRetrieveError] = useState('');
  const [retrieveLoading, setRetrieveLoading] = useState(false);
  const [builderDraftOrder, setBuilderDraftOrder] = useState<Record<string, string[]>>(() => loadFormBuilderDraftOrder(tenantId));
  const [builderVisibilityDrafts, setBuilderVisibilityDrafts] = useState<Record<string, VisibilityConditionDraft>>(() => loadFormBuilderVisibilityDrafts(tenantId));
  const [builderValidationMessage, setBuilderValidationMessage] = useState('');
  const [fieldEditorDraft, setFieldEditorDraft] = useState<FieldEditorDraft>(() => loadFieldEditorDraft(tenantId));
  const [fieldEditorValidationMessage, setFieldEditorValidationMessage] = useState('');
  const [fieldEditorSavedSummary, setFieldEditorSavedSummary] = useState('');
  const [applicationFormRuntimeState, setApplicationFormRuntimeState] = useState<ApplicationFormRuntimeState | { kind: 'loading'; message: string }>(() => ({ kind: 'loading', message: 'Loading tenant application form configuration...' }));
  const savingRef = useRef(false);
  const savePromiseRef = useRef<Promise<DraftScenario | null> | null>(null);
  const firstEntryDraftRef = useRef(false);
  const values = useMemo(() => normalizeDisplayedIntake(intake ?? localIntake), [intake, localIntake]);
  const mergedErrors = { ...errors, ...localErrors };
  const { metadata } = normalizeMetadataState(metadataState);
  const runtimeMetadata = applicationFormRuntimeState.kind === 'loaded' ? applicationFormRuntimeState.metadata : metadata;
  const orderedSteps = useMemo(() => stepsForMetadata(runtimeMetadata), [runtimeMetadata]);
  const compatibilityStartFieldIds = useMemo(() => startFieldIdsForMetadata(runtimeMetadata, values), [runtimeMetadata, values]);
  const showProgressiveCompatibility = compatibilityStartFieldIds.some((fieldId) => fieldId === 'quoteIntent');
  const sections = useMemo(() => applyBuilderDraftOrder(consolidateCanonicalFields(orderedSteps.map((step) => ({ step, fields: fieldsForStep(runtimeMetadata, step.id).map(normalizePipelineRequirement) }))), builderDraftOrder), [builderDraftOrder, orderedSteps, runtimeMetadata]);
  const runtimeSections = useMemo(() => applyRuntimeFieldVisibility(sections, values, builderVisibilityDrafts), [builderVisibilityDrafts, sections, values]);
  const visibleErrors = useMemo(() => (submitAttempted || showProgressiveCompatibility ? mergedErrors : {}), [mergedErrors, showProgressiveCompatibility, submitAttempted]);
  const draftId = draftIdFromLocation();
  const fieldDropdownOptions = useMemo(() => dropdownOptionsForFields(dropdownConfigState.options), [dropdownConfigState.options]);
  const availableFilters = useMemo(() => availableFiltersForDropdownConfig(dropdownConfigState.options), [dropdownConfigState.options]);
  const activeFilterCount = Object.values(productFilters).filter((value) => value.trim()).length;
  const filteredProducts = useMemo(() => filterProducts(tenantHomePreviewProducts, productFilters), [productFilters]);
  const priceScenarioColumns = useMemo(() => priceScenarioColumnsForMetadata(runtimeMetadata), [runtimeMetadata]);
  const lockFieldBindings = useMemo(() => lockFieldBindingsForMetadata(runtimeMetadata, values), [runtimeMetadata, values]);
  const autoConfirmationApproval = useMemo(() => autoConfirmationApprovalForMetadata(runtimeMetadata), [runtimeMetadata]);
  const requiredPricingFields = useMemo(() => visibleConfiguredRequiredFields(runtimeSections), [runtimeSections]);
  const missingRequiredFields = useMemo(() => missingConfiguredRequiredFields(requiredPricingFields, values), [requiredPricingFields, values]);
  const productGridStyle = { '--quote-product-grid-template': priceScenarioGridTemplate(priceScenarioColumns) } as CSSProperties;
  const startReady = minimumStartFields.every((field) => (values[field] ?? '').trim());
  const quoteReady = missingRequiredFields.length === 0 && Boolean(selectedProduct);
  const launchDisabled = flowState.kind === 'submitting' || !quoteReady;
  const launchStatus = quoteReady ? 'Ready to launch quote' : selectedProduct && missingRequiredFields.length > 0 ? 'Data required before pricing refresh' : 'Select a product and complete required fields';
  const completionPercent = completedFactPercent(values);

  useEffect(() => {
    if (intake) setLocalIntake(intake);
  }, [intake]);

  useEffect(() => {
    setBuilderDraftOrder(loadFormBuilderDraftOrder(tenantId));
    setBuilderVisibilityDrafts(loadFormBuilderVisibilityDrafts(tenantId));
    setBuilderValidationMessage('');
    setFieldEditorDraft(loadFieldEditorDraft(tenantId));
    setFieldEditorValidationMessage('');
    setFieldEditorSavedSummary('');
  }, [tenantId]);

  useEffect(() => {
    let cancelled = false;
    setApplicationFormRuntimeState({ kind: 'loading', message: 'Loading tenant application form configuration...' });
    fetchActiveApplicationFormVersion(tenantId)
      .then((state) => {
        if (cancelled) return;
        setApplicationFormRuntimeState(state);
        if (state.kind === 'loaded') capture('application-form-active-version-loaded', { tenantId, versionLabel: state.versionLabel });
        if (state.kind === 'missing') capture('application-form-active-version-blocked', { tenantId, reason: state.message });
      })
      .catch((error: unknown) => {
        if (cancelled) return;
        const message = error instanceof Error ? error.message : 'Published tenant application form version is temporarily unavailable.';
        setApplicationFormRuntimeState({ kind: 'unreachable', message });
        capture('application-form-active-version-unreachable', { tenantId, reason: message });
      });
    return () => { cancelled = true; };
  }, [tenantId]);

  useEffect(() => {
    const cached = tenantDropdownConfigCache.get(tenantId);
    if (cached && cached.kind !== 'loading') {
      setDropdownConfigState(cached);
      return;
    }

    let cancelled = false;
    setDropdownConfigState({ kind: 'loading', options: cached?.options ?? fallbackTenantDropdownOptions(), message: 'Loading tenant dropdown options...' });
    fetchTenantDropdownOptions(tenantId)
      .then((options) => {
        const readyState: DropdownConfigState = { kind: 'ready', options: mergeTenantDropdownOptions(options), message: 'Tenant dropdown options loaded.' };
        tenantDropdownConfigCache.set(tenantId, readyState);
        if (!cancelled) setDropdownConfigState(readyState);
      })
      .catch((error: unknown) => {
        const message = error instanceof Error ? error.message : 'Tenant dropdown configuration is unavailable.';
        const fallbackState = fallbackDropdownConfigState(`${message} LoanPass enum defaults are in use.`);
        tenantDropdownConfigCache.set(tenantId, fallbackState);
        if (!cancelled) setDropdownConfigState(fallbackState);
      });
    return () => { cancelled = true; };
  }, [tenantId]);

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

  function changeBuilderCondition(fieldId: string, patch: Partial<VisibilityConditionDraft>) {
    setBuilderValidationMessage('');
    setBuilderVisibilityDrafts((current) => {
      const parentFields = builderParentFields(sections, fieldId);
      const existing = current[fieldId] ?? defaultVisibilityCondition(parentFields[0]?.fieldId);
      const parentFieldId = patch.parentFieldId ?? existing.parentFieldId;
      const operator = patch.operator ?? (patch.parentFieldId ? defaultOperatorForParent(parentFieldForCondition(sections, parentFieldId), fieldDropdownOptions) : existing.operator);
      const values = patch.values ?? (patch.parentFieldId || patch.operator ? [] : existing.values);
      const next = { ...current, [fieldId]: { parentFieldId, operator, values } };
      saveFormBuilderVisibilityDrafts(tenantId, next);
      return next;
    });
  }

  function clearBuilderCondition(fieldId: string) {
    setBuilderValidationMessage('');
    setBuilderVisibilityDrafts((current) => {
      const next = { ...current };
      delete next[fieldId];
      saveFormBuilderVisibilityDrafts(tenantId, next);
      return next;
    });
  }

  function saveFieldEditorDraft() {
    const validation = validateFieldEditorDraft(fieldEditorDraft);
    if (validation) {
      setFieldEditorValidationMessage(validation);
      setFieldEditorSavedSummary('');
      setStatusMessage(validation);
      capture('form-builder-field-editor-blocked', { tenantId, reason: validation });
      return;
    }
    const serialized = serializeFieldEditorDraft(fieldEditorDraft);
    saveFieldEditorDraftRecord(tenantId, serialized);
    setFieldEditorValidationMessage('');
    setFieldEditorSavedSummary(`Field editor draft saved for ${serialized.fieldId} as ${fieldEditorDataTypeLabel(serialized.dataType)}.`);
    setStatusMessage('Application form field editor data type saved for this tenant draft.');
    capture('form-builder-field-editor-saved', { tenantId, fieldId: serialized.fieldId, dataType: serialized.dataType, hasConstraints: Object.keys(serialized.constraints).length > 0 });
  }

  function setFilter(key: keyof ProductFilterState, value: string) {
    setProductFilters((current) => ({ ...current, [key]: value }));
    if (key === 'productType') changeField('mortgageType', value);
    if (key === 'investor') changeField('investorCode', value);
    if (key === 'channel') {
      changeField('channel', value);
      changeField('channelCode', value);
    }
    if (key === 'ltvMin' || key === 'ltvMax') changeField('loanToValue', value);
    if (key === 'ficoMin' || key === 'ficoMax') changeField('decisionCreditScore', value);
    if (key === 'loanAmountMin' || key === 'loanAmountMax') changeField('baseLoanAmount', value);
    if (key === 'term') changeField('desiredLoanTerm', value);
    if (key === 'propertyType') changeField('propertyType', value);
    if (key === 'occupancy') changeField('occupancyType', value);
  }

  function applyProduct(product: AuthorizedProduct) {
    if (!isProductEligible(product)) {
      setRowActionState({ productCode: product.productCode, tone: 'blocked', message: `${product.productName} is ${friendlyLabel(product.status)} and cannot be selected for launch.`, details: [productUnavailableReason(product)] });
      return;
    }
    const channel = product.channelCode;
    const mortgageType = product.productType;
    changeField('channel', channel);
    changeField('channelCode', product.channelCode);
    changeField('investorCode', product.investorCode);
    changeField('mortgageType', mortgageType);
    setSelectedProduct(product);
    setProductFilters((current) => ({ ...current, productType: product.productType, investor: product.investorCode, channel: product.channelCode, status: product.status }));
    setStatusMessage(`${product.productName} selected.`);
    capture('pipeline-product-selected', { productCode: product.productCode, investor: product.investorCode, channel: channelLabel(channel) });
  }

  function selectProduct(product: AuthorizedProduct) {
    setSelectedProduct(product);
    setStatusMessage(`${product.productName} row selected.`);
    capture('pipeline-row-selected', { productCode: product.productCode, status: product.status });
  }

  function requestRowAction(product: AuthorizedProduct, action: ProductRowAction) {
    if (action !== 'status' && !isProductEligible(product)) {
      setRowActionState({ productCode: product.productCode, tone: 'blocked', message: `${product.productName} is ${friendlyLabel(product.status)}; ${actionLabel(action)} is unavailable.`, details: [productUnavailableReason(product)] });
      return;
    }

    if (action === 'refresh') {
      setRowActionState({ productCode: product.productCode, tone: 'ready', message: `Pricing refresh requested for ${product.productName}.`, details: ['No pricing rules were changed in the row action.'] });
      capture('pipeline-row-refresh-requested', { productCode: product.productCode });
      return;
    }

    if (action === 'lock') {
      const missingLockConfig = lockFieldBindings.length === 0;
      setRowActionState({
        productCode: product.productCode,
        tone: missingLockConfig ? 'blocked' : autoConfirmationApproval.required ? 'attention' : 'ready',
        message: missingLockConfig
          ? 'Lock field configuration is unavailable for this row.'
          : autoConfirmationApproval.required
            ? `Approval required before lock, reprice, relock, or cancellation. ${autoConfirmationApproval.path}`
            : `Lock request prepared for ${product.productName}.`,
        details: lockFieldBindings.map((binding) => `${binding.label}: ${binding.configuredFieldId} = ${binding.value || 'Not yet captured'}`),
      });
      capture('pipeline-row-lock-requested', { productCode: product.productCode, approvalRequired: autoConfirmationApproval.required, lockFields: lockFieldBindings.map((binding) => binding.configuredFieldId) });
      return;
    }

    setRowActionState({ productCode: product.productCode, tone: isProductEligible(product) ? 'ready' : 'blocked', message: `${product.productName} status: ${friendlyLabel(product.status)}.`, details: [productUnavailableReason(product)] });
    capture('pipeline-row-status-reviewed', { productCode: product.productCode, status: product.status });
  }

  function clearFilters() {
    setProductFilters({ ...emptyProductFilters, status: '' });
    setStatusMessage('Filters cleared.');
  }

  function moveBuilderField(section: QuoteIntakeSection, fieldId: string, direction: -1 | 1) {
    setBuilderDraftOrder((current) => {
      const sectionFields = sections.find((candidate) => candidate.step.section === section)?.fields ?? [];
      const existing = current[section] ?? sectionFields.map((field) => String(field.fieldId));
      const index = existing.indexOf(fieldId);
      const nextIndex = index + direction;
      if (index === -1 || nextIndex < 0 || nextIndex >= existing.length) return current;
      const next = [...existing];
      [next[index], next[nextIndex]] = [next[nextIndex], next[index]];
      return { ...current, [section]: next };
    });
  }

  function saveBuilderDraftOrder() {
    const validation = validateBuilderVisibilityDrafts(sections, builderVisibilityDrafts);
    if (validation) {
      setBuilderValidationMessage(validation);
      setStatusMessage(validation);
      capture('form-builder-conditional-visibility-blocked', { tenantId, reason: validation });
      return;
    }
    const order = Object.fromEntries(sections.map(({ step, fields }) => [step.section, fields.map((field) => String(field.fieldId))]));
    saveFormBuilderDraftOrder(tenantId, order);
    saveFormBuilderVisibilityDrafts(tenantId, builderVisibilityDrafts);
    setBuilderDraftOrder(order);
    setBuilderValidationMessage('');
    setStatusMessage('Application form builder draft order and conditional visibility saved for this tenant.');
    capture('form-builder-draft-order-saved', { tenantId, sections: Object.keys(order), conditionalFields: Object.keys(builderVisibilityDrafts), storageScope: 'tenant-draft-only' });
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
      const launchValues = product ? { ...values, channel: product.channelCode, channelCode: product.channelCode, investorCode: product.investorCode, mortgageType: product.productType } : values;
    const validationErrors = validateFields(requiredPricingFields, launchValues);
    if (Object.keys(validationErrors).length > 0) {
      setLocalErrors(validationErrors);
      setFlowState({ kind: 'blocked', validation: errorsToValidation(validationErrors, 'Data required before pricing refresh.') });
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
    return fieldIds.map((fieldId) => fieldForId(runtimeSections, fieldId)).filter((field): field is ScenarioIntakeField => Boolean(field));
  }

  function startFields() {
    if (showProgressiveCompatibility) return [];
    return compatibilityStartFieldIds.map((fieldId) => {
      const field = fieldForId(sections, fieldId);
      return field ? { ...field, label: minimumStartFieldLabel(fieldId), required: true, dataType: 'text' as const } : fallbackStartField(fieldId);
    });
  }

  function quoteFields() {
    const fields = fieldsById(minimumQuoteFields).map((field) => ({ ...field, required: fieldPipelineRequired(field) }));
    return fields.length > 0 ? fields : startFields();
  }

  function focusNextRequiredField() {
    const nextErrors = validateFields(requiredPricingFields, values);
    setSubmitAttempted(true);
    setLocalErrors(nextErrors);
    setFlowState({ kind: 'blocked', validation: errorsToValidation(nextErrors, 'Data required before pricing refresh.') });
    const activeId = document.activeElement?.id;
    const activeIndex = missingRequiredFields.findIndex((field) => String(field.fieldId) === activeId);
    const nextField = missingRequiredFields[(activeIndex + 1) % missingRequiredFields.length] ?? missingRequiredFields[0];
    if (nextField) window.setTimeout(() => document.getElementById(String(nextField.fieldId))?.focus(), 0);
    capture('pipeline-data-required-next-field', { fieldIds: missingRequiredFields.map((field) => String(field.fieldId)), nextFieldId: nextField ? String(nextField.fieldId) : '' });
  }

  function quoteDetailFields() {
    return fieldsById(['loanPurpose', 'documentationType', 'decisionCreditScore', 'baseLoanAmount', 'state', 'zip']).map((field) => ({
      ...field,
      required: minimumQuoteFields.includes(field.fieldId),
    }));
  }

  function capture(action: string, detail: Record<string, unknown>) {
    onEvidenceCapture?.({ action, storyId: 'PIPE-PII-59-S04', ...detail });
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
          <InlineSelectField label="Channel" name="channel" value={values.channel ?? ''} error={visibleErrors.channel} options={fieldDropdownOptions.channel ?? []} loading={dropdownConfigState.kind === 'loading'} onChange={(value) => changeField('channel', value)} />
        </div>
      ) : null}
      {dropdownConfigState.kind === 'loading' ? <p className="quote-intake-status" role="status">Loading tenant dropdown options...</p> : null}
      {dropdownConfigState.kind === 'fallback' ? <p className="quote-intake-status" aria-live="polite">{dropdownConfigState.message}</p> : null}
      {applicationFormRuntimeState.kind === 'loading' ? <p className="quote-intake-status" role="status">Loading tenant application form configuration...</p> : null}
      {applicationFormRuntimeState.kind === 'loaded' ? <p className="quote-intake-status" aria-live="polite">Published application form version {applicationFormRuntimeState.versionLabel} loaded for {tenantId}.</p> : null}
      {applicationFormRuntimeState.kind === 'missing' || applicationFormRuntimeState.kind === 'unreachable' ? <p className="quote-intake-status" role="alert">Application form configuration blocker: {applicationFormRuntimeState.message}</p> : null}
      {statusMessage ? <p className="quote-intake-status" role="status">{statusMessage}</p> : null}
      <DataRequiredPanel fields={missingRequiredFields} onNextField={focusNextRequiredField} />

      <form id="pipeline-product-form" className="quote-intake-form quote-intake-form--product-finder quote-intake-form--single-page" onSubmit={(event) => void launch(event)} onBlur={handleBlur} noValidate>
        <aside className="quote-product-filter-panel" aria-label="Pipeline filters">
          <ApplicationFormBuilderPanel sections={sections} conditions={builderVisibilityDrafts} dropdownOptions={fieldDropdownOptions} validationMessage={builderValidationMessage} fieldEditorDraft={fieldEditorDraft} fieldEditorValidationMessage={fieldEditorValidationMessage} fieldEditorSavedSummary={fieldEditorSavedSummary} onFieldEditorDraftChange={setFieldEditorDraft} onFieldEditorSave={saveFieldEditorDraft} onMove={moveBuilderField} onConditionChange={changeBuilderCondition} onConditionClear={clearBuilderCondition} onSave={saveBuilderDraftOrder} />

          {applicationFormRuntimeState.kind === 'loading' ? null : <ApplicationFormRuntimeRenderer sections={applicationFormRuntimeState.kind === 'loaded' ? runtimeSections : []} values={values} errors={visibleErrors} dropdownOptions={fieldDropdownOptions} dropdownLoading={dropdownConfigState.kind === 'loading'} onChange={changeField} />}

          <FilterCard title="Keys" eyebrow="Save" badge={startReady ? 'Ready' : 'Required fields'}>
            <StepFields fields={startFields()} intake={values} errors={visibleErrors} onChange={changeField} dropdownOptions={fieldDropdownOptions} dropdownLoading={dropdownConfigState.kind === 'loading'} />
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
            <SelectFilter label="Property type" value={productFilters.propertyType} values={optionValues(fieldDropdownOptions.propertyType)} onChange={(value) => setFilter('propertyType', value)} formatter={friendlyLabel} />
            <SelectFilter label="Occupancy" value={productFilters.occupancy} values={optionValues(fieldDropdownOptions.occupancyType)} onChange={(value) => setFilter('occupancy', value)} formatter={friendlyLabel} />
            <button type="button" className="quote-filter-clear" onClick={clearFilters}>Clear</button>
          </FilterCard>

          <FilterCard title="Quote" eyebrow="Launch" badge={quoteReady ? 'Ready' : 'Required fields'}>
            <StepFields fields={quoteDetailFields()} intake={values} errors={visibleErrors} onChange={changeField} dropdownOptions={fieldDropdownOptions} dropdownLoading={dropdownConfigState.kind === 'loading'} />
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

          <div className="quote-product-grid quote-product-grid--rows" role="table" aria-label="Filtered mortgage products" style={productGridStyle}>
            <div className="quote-product-row quote-product-row--header" role="row">
              {priceScenarioColumns.map((column) => <div key={column.key} role="columnheader">{column.label}</div>)}
            </div>
            {filteredProducts.map((product) => (
              <ProductResultRow key={product.productCode} product={product} values={values} columns={priceScenarioColumns} selected={selectedProduct?.productCode === product.productCode} actionState={rowActionState?.productCode === product.productCode ? rowActionState : null} onSelect={selectProduct} onApply={applyProduct} onAction={requestRowAction} />
            ))}
          </div>
        </section>

        <div className="quote-pipeline-launchbar">
          <div>
            <strong>{selectedProduct?.productName ?? 'No product selected'}</strong>
            <span id="quote-launch-state">{launchStatus}</span>
          </div>
          <button type="submit" className="quote-intake-primary" disabled={launchDisabled} aria-describedby="quote-launch-state">{flowState.kind === 'submitting' ? 'Launching...' : 'Launch Quote'}</button>
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

function ApplicationFormRuntimeRenderer({
  sections,
  values,
  errors,
  dropdownOptions,
  dropdownLoading,
  onChange,
}: {
  sections: Array<{ step: QuoteIntakeStepDefinition; fields: ScenarioIntakeField[] }>;
  values: BorrowerIntake;
  errors: IntakeFieldErrors;
  dropdownOptions: DropdownOptionsByField;
  dropdownLoading: boolean;
  onChange: (field: keyof BorrowerIntake, value: string) => void;
}) {
  const visibleSections = sections.filter(({ fields }) => fields.length > 0);
  if (visibleSections.length === 0) {
    return (
      <FilterCard title="Application Form" eyebrow="Runtime" badge="Blocked">
        <p className="quote-intake-empty" role="status">No published application form fields are configured for runtime rendering.</p>
      </FilterCard>
    );
  }
  return (
    <FilterCard title="Application Form" eyebrow="Runtime" badge="Tenant config">
      <div className="quote-runtime-form" aria-label="Runtime application form">
        {visibleSections.map(({ step, fields }) => (
          <section key={step.section} className="quote-runtime-form__section" aria-labelledby={`runtime-${step.section}-heading`}>
            <h4 id={`runtime-${step.section}-heading`}>{step.label}</h4>
            <StepFields fields={fields} intake={values} errors={errors} onChange={onChange} dropdownOptions={dropdownOptions} dropdownLoading={dropdownLoading} />
          </section>
        ))}
      </div>
    </FilterCard>
  );
}

function DataRequiredPanel({ fields, onNextField }: { fields: PipelineRequiredField[]; onNextField: () => void }) {
  if (fields.length === 0) return null;
  const fieldIds = fields.map((field) => String(field.fieldId));
  return (
    <section className="quote-intake-data-required" aria-labelledby="data-required-heading" data-required-field-ids={fieldIds.join(' ')}>
      <div>
        <p className="eyebrow">Data required</p>
        <h3 id="data-required-heading">Complete required fields</h3>
        <p>Pricing refresh is waiting on {fields.length} visible configured required {fields.length === 1 ? 'field' : 'fields'}.</p>
      </div>
      <ul aria-label="Missing configured required fields">
        {fields.map((field) => <li key={String(field.fieldId)} data-field-id={String(field.fieldId)}>{field.label}<code>{String(field.fieldId)}</code></li>)}
      </ul>
      <button type="button" className="quote-filter-clear" onClick={onNextField}>Next Field</button>
    </section>
  );
}

function ApplicationFormBuilderPanel({
  sections,
  conditions,
  dropdownOptions,
  validationMessage,
  fieldEditorDraft,
  fieldEditorValidationMessage,
  fieldEditorSavedSummary,
  onFieldEditorDraftChange,
  onFieldEditorSave,
  onMove,
  onConditionChange,
  onConditionClear,
  onSave,
}: {
  sections: Array<{ step: QuoteIntakeStepDefinition; fields: ScenarioIntakeField[] }>;
  conditions: Record<string, VisibilityConditionDraft>;
  dropdownOptions: DropdownOptionsByField;
  validationMessage: string;
  fieldEditorDraft: FieldEditorDraft;
  fieldEditorValidationMessage: string;
  fieldEditorSavedSummary: string;
  onFieldEditorDraftChange: (draft: FieldEditorDraft) => void;
  onFieldEditorSave: () => void;
  onMove: (section: QuoteIntakeSection, fieldId: string, direction: -1 | 1) => void;
  onConditionChange: (fieldId: string, patch: Partial<VisibilityConditionDraft>) => void;
  onConditionClear: (fieldId: string) => void;
  onSave: () => void;
}) {
  const typedPreview = renderFieldEditorPreview(fieldEditorDraft);
  return (
    <section className="quote-filter-card quote-form-builder" aria-labelledby="application-form-builder-heading">
      <div className="quote-filter-card__heading">
        <div>
          <p className="eyebrow">Builder</p>
          <h3 id="application-form-builder-heading">Application form order</h3>
        </div>
        <span>Tenant draft</span>
      </div>
      <p className="quote-form-builder__copy">Headers separate runtime sections and never require borrower input. Field order and conditional visibility rules are saved to this tenant draft only.</p>
      {validationMessage ? <p className="quote-intake-error" role="alert">{validationMessage}</p> : null}
      <fieldset className="quote-form-builder__field-editor" aria-label="Custom field editor data types">
        <legend>Field editor</legend>
        <p className="quote-form-builder__copy">Configure field-library data types without creating pricing formulas. Enum values remain owned by approved enum workflows.</p>
        {fieldEditorValidationMessage ? <p className="quote-intake-error" role="alert">{fieldEditorValidationMessage}</p> : null}
        {fieldEditorSavedSummary ? <p className="quote-intake-status" role="status">{fieldEditorSavedSummary}</p> : null}
        <label>Field Name<input required value={fieldEditorDraft.fieldName} onChange={(event) => onFieldEditorDraftChange({ ...fieldEditorDraft, fieldName: event.target.value })} /></label>
        <label>Field ID<input required value={fieldEditorDraft.fieldId} onChange={(event) => onFieldEditorDraftChange({ ...fieldEditorDraft, fieldId: event.target.value })} placeholder="field@approved-reference" /></label>
        <label>Field Description<textarea required value={fieldEditorDraft.fieldDescription} onChange={(event) => onFieldEditorDraftChange({ ...fieldEditorDraft, fieldDescription: event.target.value })} /></label>
        <label>Data Type<select required value={fieldEditorDraft.dataType} onChange={(event) => onFieldEditorDraftChange({ ...fieldEditorDraft, dataType: event.target.value })}>{fieldEditorDataTypes.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}</select></label>
        {fieldEditorDraft.dataType === 'enum' ? <label>Enum Type<input value={fieldEditorDraft.enumTypeId} onChange={(event) => onFieldEditorDraftChange({ ...fieldEditorDraft, enumTypeId: event.target.value })} placeholder="approved-enum-type-id" /></label> : null}
        {fieldEditorDraft.dataType === 'number' || fieldEditorDraft.dataType === 'duration' ? (
          <div className="quote-form-builder__constraints" aria-label={`${fieldEditorDataTypeLabel(fieldEditorDraft.dataType)} constraints`}>
            <label>Precision<input value={fieldEditorDraft.precision} inputMode="decimal" onChange={(event) => onFieldEditorDraftChange({ ...fieldEditorDraft, precision: event.target.value })} /></label>
            <label>Style<input value={fieldEditorDraft.style} onChange={(event) => onFieldEditorDraftChange({ ...fieldEditorDraft, style: event.target.value })} /></label>
            <label>Units<input value={fieldEditorDraft.units} onChange={(event) => onFieldEditorDraftChange({ ...fieldEditorDraft, units: event.target.value })} /></label>
            <label>Minimum<input value={fieldEditorDraft.minimum} inputMode="decimal" onChange={(event) => onFieldEditorDraftChange({ ...fieldEditorDraft, minimum: event.target.value })} /></label>
            <label>Maximum<input value={fieldEditorDraft.maximum} inputMode="decimal" onChange={(event) => onFieldEditorDraftChange({ ...fieldEditorDraft, maximum: event.target.value })} /></label>
          </div>
        ) : null}
        <div className="quote-form-builder__field-preview" aria-label="Field editor render preview">{typedPreview}</div>
        <button type="button" className="quote-filter-clear" onClick={onFieldEditorSave}>Save field editor data type</button>
      </fieldset>
      <ol className="quote-form-builder__sections" aria-label="Configured sections and fields">
        {sections.map(({ step, fields }) => (
          <li key={step.section}>
            <strong>{step.label}</strong>
            <ol className="quote-form-builder__fields">
              {fields.map((field, index) => {
                const fieldId = String(field.fieldId);
                const header = isHeaderMetadataField(field);
                const condition = conditions[fieldId];
                const parentFields = builderParentFields(sections, fieldId);
                const parentField = condition ? parentFieldForCondition(sections, condition.parentFieldId) : null;
                const operators = parentField ? visibilityOperatorsForParent(parentField, dropdownOptions) : [];
                const valueOptions = condition?.parentFieldId ? conditionValueOptions(condition.parentFieldId, dropdownOptions) : [];
                return (
                  <li key={fieldId} data-field-id={fieldId} data-value-type={header ? 'header' : 'input'}>
                    <span>{field.label}</span>
                    {header ? <em>section header</em> : null}
                    <div aria-label={`${field.label} order controls`}>
                      <button type="button" onClick={() => onMove(step.section, fieldId, -1)} disabled={index === 0}>Move up</button>
                      <button type="button" onClick={() => onMove(step.section, fieldId, 1)} disabled={index === fields.length - 1}>Move down</button>
                    </div>
                    {!header ? (
                      <fieldset className="quote-form-builder__condition" aria-label={`${field.label} conditional visibility`}>
                        <legend>Conditional visibility</legend>
                        <label>
                          Parent field
                          <select value={condition?.parentFieldId ?? ''} onChange={(event) => (event.target.value ? onConditionChange(fieldId, { parentFieldId: event.target.value as keyof BorrowerIntake }) : onConditionClear(fieldId))}>
                            <option value="">Always visible</option>
                            {parentFields.map((parent) => <option key={String(parent.fieldId)} value={String(parent.fieldId)}>{parent.label}</option>)}
                          </select>
                        </label>
                        {condition?.parentFieldId ? (
                          <>
                            <label>
                              Operator
                              <select value={condition.operator} onChange={(event) => onConditionChange(fieldId, { operator: event.target.value as VisibilityConditionOperator })}>
                                {operators.map((operator) => <option key={operator} value={operator}>{visibilityOperatorLabel(operator)}</option>)}
                              </select>
                            </label>
                            {!operatorNeedsNoValue(condition.operator) ? (
                              <label>
                                Condition value
                                {valueOptions.length > 0 ? (
                                  <select value={condition.values[0] ?? ''} onChange={(event) => onConditionChange(fieldId, { values: event.target.value ? [event.target.value] : [] })}>
                                    <option value="">Select value</option>
                                    {valueOptions.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
                                  </select>
                                ) : (
                                  <input value={condition.values[0] ?? ''} onChange={(event) => onConditionChange(fieldId, { values: event.target.value ? [event.target.value] : [] })} />
                                )}
                              </label>
                            ) : null}
                            <button type="button" onClick={() => onConditionClear(fieldId)}>Clear condition</button>
                          </>
                        ) : null}
                      </fieldset>
                    ) : null}
                  </li>
                );
              })}
            </ol>
          </li>
        ))}
      </ol>
      <button type="button" className="quote-filter-clear" onClick={onSave}>Save form draft order</button>
    </section>
  );
}

function renderFieldEditorPreview(draft: FieldEditorDraft) {
  const fieldLabel = draft.fieldName.trim() || 'Custom field preview';
  const help = draft.fieldDescription.trim() || 'Field description is required before saving.';
  if (draft.dataType === 'enum') {
    return <label>{fieldLabel}<select disabled aria-describedby="field-editor-preview-help"><option>{draft.enumTypeId.trim() ? `Enum options from ${draft.enumTypeId.trim()}` : 'Select approved enum type first'}</option></select><small id="field-editor-preview-help">{help}</small></label>;
  }
  if (draft.dataType === 'number') {
    return <label>{fieldLabel}<input type="number" disabled aria-describedby="field-editor-preview-help" placeholder={constraintRangeText(draft)} /><small id="field-editor-preview-help">{help}</small></label>;
  }
  if (draft.dataType === 'duration') {
    return <label>{fieldLabel}<input disabled aria-describedby="field-editor-preview-help" placeholder={durationConstraintText(draft)} /><small id="field-editor-preview-help">{help}</small></label>;
  }
  if (draft.dataType === 'date') return <label>{fieldLabel}<input type="date" disabled aria-describedby="field-editor-preview-help" /><small id="field-editor-preview-help">{help}</small></label>;
  if (draft.dataType === 'time') return <label>{fieldLabel}<input type="time" disabled aria-describedby="field-editor-preview-help" /><small id="field-editor-preview-help">{help}</small></label>;
  return <label>{fieldLabel}<input disabled aria-describedby="field-editor-preview-help" /><small id="field-editor-preview-help">{help}</small></label>;
}

function constraintRangeText(draft: FieldEditorDraft) {
  const parts = [draft.minimum.trim() ? `min ${draft.minimum.trim()}` : '', draft.maximum.trim() ? `max ${draft.maximum.trim()}` : '', draft.precision.trim() ? `precision ${draft.precision.trim()}` : ''].filter(Boolean);
  return parts.join(', ') || 'Numeric value';
}

function durationConstraintText(draft: FieldEditorDraft) {
  const parts = [draft.units.trim() ? `units ${draft.units.trim()}` : '', draft.style.trim() ? `style ${draft.style.trim()}` : '', constraintRangeText(draft)].filter(Boolean);
  return parts.join(', ');
}

function InlineSelectField({ label, name, value, error, options, loading, onChange }: { label: string; name: keyof BorrowerIntake; value: string; error?: string; options: DropdownOption[]; loading: boolean; onChange: (value: string) => void }) {
  const renderedOptions = loading ? [{ value: '', label: 'Loading options...' }] : withCurrentOption(withPlaceholder(options, `Select ${label.toLowerCase()}`), value);
  return (
    <label className="quote-intake-field">
      {label}
      <select name={name} value={value} aria-invalid={Boolean(error)} onChange={(event) => onChange(event.target.value)} disabled={loading}>
        {renderedOptions.map((option) => <option key={option.value || 'empty'} value={option.value}>{option.label}</option>)}
      </select>
      {error ? <p className="quote-intake-error" role="alert">{error}</p> : null}
    </label>
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

function ProductResultRow({ product, values, columns, selected, actionState, onSelect, onApply, onAction }: { product: AuthorizedProduct; values: BorrowerIntake; columns: PriceScenarioColumn[]; selected: boolean; actionState: ProductRowActionState | null; onSelect: (product: AuthorizedProduct) => void; onApply: (product: AuthorizedProduct) => void; onAction: (product: AuthorizedProduct, action: ProductRowAction) => void }) {
  return (
    <div className="quote-product-row" role="row" aria-label={`${product.productName} ${product.status}`} aria-selected={selected} data-selected={selected} data-product-status={product.status.toLowerCase()} onClick={() => onSelect(product)} tabIndex={0} onKeyDown={(event) => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); onSelect(product); } }}>
      {columns.map((column) => <div key={column.key} className={`quote-product-cell quote-product-cell--${column.key}`} role="cell" data-column={column.label}>{renderProductColumn(column.key, product, values, onApply, onAction, actionState, column)}</div>)}
    </div>
  );
}

function renderProductColumn(key: PriceScenarioColumnKey, product: AuthorizedProduct, values: BorrowerIntake, onApply: (product: AuthorizedProduct) => void, onAction: (product: AuthorizedProduct, action: ProductRowAction) => void, actionState: ProductRowActionState | null, column?: PriceScenarioColumn) {
  if (key === 'product') return <div className="quote-product-cell__product"><strong>{product.productName}</strong><span>{product.productCode}</span><small>{productAvailabilityLabel(product)}</small></div>;
  if (key === 'investor') return product.investorCode;
  if (key === 'status') return <span className={`quote-status-pill quote-status-pill--${statusClass(product.status)}`}>{product.status}</span>;
  if (key === 'adjustedRate') return rateIndicator(product);
  if (key === 'lockPeriod') return values.desiredRateLockPeriod || values.requestedLockPeriodDays || termFromProductName(product.productName);
  if (key === 'adjustedPrice') return values.secondaryAdjustment || '—';
  if (key === 'payment') return values.totalLiabilityMonthlyPayment || '—';
  if (key === 'pitiaItia') return values.additionalMonthlyHousingExpenses || values.additionalAnnualHousingExpenses || '—';
  if (key === 'dscr') return values.estimatedDSCR || '—';
  if (key === 'ltv') return values.loanToValue || '—';
  if (key === 'fico') return values.decisionCreditScore || '—';
  if (column?.fieldId || isCalculationFieldId(key)) return renderCalculationOutput(column ?? { key, label: friendlyLabel(key), fieldId: key }, values);
  return (
    <div className="quote-product-row-actions" aria-label={`${product.productName} row actions`}>
      <button type="button" onClick={(event) => { event.stopPropagation(); onApply(product); }} disabled={!isProductEligible(product)}>Use</button>
      <button type="button" onClick={(event) => { event.stopPropagation(); onAction(product, 'refresh'); }} disabled={!isProductEligible(product)}>Refresh</button>
      <button type="button" onClick={(event) => { event.stopPropagation(); onAction(product, 'lock'); }} disabled={!isProductEligible(product)}>Request lock</button>
      <button type="button" onClick={(event) => { event.stopPropagation(); onAction(product, 'status'); }}>Status</button>
      {actionState ? (
        <div className={`quote-product-row-action-status quote-product-row-action-status--${actionState.tone}`} role="status" onClick={(event) => event.stopPropagation()}>
          <strong>{actionState.message}</strong>
          {actionState.details.length > 0 ? <ul>{actionState.details.map((detail) => <li key={detail}>{detail}</li>)}</ul> : null}
        </div>
      ) : null}
    </div>
  );
}

function renderCalculationOutput(column: PriceScenarioColumn, values: BorrowerIntake) {
  const fieldId = column.fieldId ?? column.key;
  const value = calculationOutputValue(values, fieldId);
  if (value) return <span data-calculation-field-id={fieldId}>{value}</span>;
  return <span data-calculation-field-id={fieldId} data-calculation-state="missing">{column.blockedLabel ?? column.missingLabel ?? 'Calculation output unavailable'}</span>;
}

function calculationOutputValue(values: BorrowerIntake, fieldId: string) {
  const direct = (values as Record<string, string | undefined>)[fieldId]?.trim();
  if (direct) return direct;
  const alias = calculationOutputAliases[normalized(fieldId)];
  return alias ? ((values as Record<string, string | undefined>)[alias] ?? '').trim() : '';
}

const calculationOutputAliases: Record<string, keyof BorrowerIntake> = {
  calcadjustedprice: 'secondaryAdjustment',
  calcltv: 'loanToValue',
};

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

function applyBuilderDraftOrder(sections: Array<{ step: QuoteIntakeStepDefinition; fields: ScenarioIntakeField[] }>, draftOrder: Record<string, string[]>) {
  return sections.map(({ step, fields }) => {
    const orderedIds = draftOrder[step.section] ?? [];
    if (orderedIds.length === 0) return { step, fields };
    return {
      step,
      fields: [...fields].sort((left, right) => orderIndexForField(left, orderedIds) - orderIndexForField(right, orderedIds)),
    };
  });
}

function orderIndexForField(field: ScenarioIntakeField, orderedIds: string[]) {
  const index = orderedIds.indexOf(String(field.fieldId));
  return index === -1 ? orderedIds.length : index;
}

function applyRuntimeFieldVisibility(sections: Array<{ step: QuoteIntakeStepDefinition; fields: ScenarioIntakeField[] }>, values: BorrowerIntake, drafts: Record<string, VisibilityConditionDraft>) {
  return sections.map(({ step, fields }) => ({
    step,
    fields: fields.filter((field) => isHeaderMetadataField(field) || evaluateFieldVisibility({ ...field, visibilityCondition: drafts[String(field.fieldId)] ?? field.visibilityCondition }, values)),
  }));
}

function evaluateFieldVisibility(field: ScenarioIntakeField, values: BorrowerIntake) {
  const condition = field.visibilityCondition;
  if (!condition) return true;
  const parentValue = values[condition.parentFieldId] ?? '';
  const expectedValues = condition.values.map((value) => normalized(value));
  const normalizedParent = normalized(parentValue);
  if (condition.operator === 'empty') return !parentValue.trim();
  if (condition.operator === 'not_empty') return Boolean(parentValue.trim());
  if (condition.operator === 'equals') return expectedValues.includes(normalizedParent);
  if (condition.operator === 'not_equals') return !expectedValues.includes(normalizedParent);
  if (condition.operator === 'any_of') return expectedValues.includes(normalizedParent);
  if (condition.operator === 'contains') return expectedValues.some((value) => normalizedParent.includes(value));
  if (condition.operator === 'greater_than') return Number(parentValue) > Number(condition.values[0]);
  if (condition.operator === 'less_than') return Number(parentValue) < Number(condition.values[0]);
  return true;
}

function validateBuilderVisibilityDrafts(sections: Array<{ step: QuoteIntakeStepDefinition; fields: ScenarioIntakeField[] }>, drafts: Record<string, VisibilityConditionDraft>) {
  const activeParentIds = new Set(sections.flatMap(({ fields }) => fields).filter((field) => !isHeaderMetadataField(field) && configuredBuilderParentActive(field)).map((field) => String(field.fieldId)));
  const fieldLabels = new Map(sections.flatMap(({ fields }) => fields).map((field) => [String(field.fieldId), field.label]));
  for (const [fieldId, condition] of Object.entries(drafts)) {
    if (!activeParentIds.has(String(condition.parentFieldId))) {
      return `Condition for ${fieldLabels.get(fieldId) ?? fieldId} references hidden or removed parent field ${String(condition.parentFieldId)}.`;
    }
    if (!operatorNeedsNoValue(condition.operator) && condition.values.length === 0) {
      return `Condition for ${fieldLabels.get(fieldId) ?? fieldId} requires a condition value.`;
    }
  }
  return '';
}

function builderParentFields(sections: Array<{ step: QuoteIntakeStepDefinition; fields: ScenarioIntakeField[] }>, fieldId: string) {
  return sections
    .flatMap(({ fields }) => fields)
    .filter((field) => String(field.fieldId) !== fieldId && !isHeaderMetadataField(field) && configuredBuilderParentActive(field));
}

function parentFieldForCondition(sections: Array<{ step: QuoteIntakeStepDefinition; fields: ScenarioIntakeField[] }>, fieldId: keyof BorrowerIntake) {
  return sections.flatMap(({ fields }) => fields).find((field) => field.fieldId === fieldId) ?? null;
}

function defaultVisibilityCondition(parentFieldId?: keyof BorrowerIntake): VisibilityConditionDraft {
  return { parentFieldId: parentFieldId ?? 'mortgageType', operator: 'equals', values: [] };
}

function configuredBuilderParentActive(field: ScenarioIntakeField) {
  const record = field as ScenarioIntakeField & { active?: boolean; visible?: boolean; removed?: boolean; isRemoved?: boolean };
  return record.active !== false && record.visible !== false && record.removed !== true && record.isRemoved !== true;
}

function visibilityOperatorsForParent(field: ScenarioIntakeField, dropdownOptions: DropdownOptionsByField): VisibilityConditionOperator[] {
  if (conditionValueOptions(field.fieldId, dropdownOptions).length > 0) return ['equals', 'not_equals', 'any_of', 'empty', 'not_empty'];
  if (field.dataType === 'number') return ['equals', 'not_equals', 'greater_than', 'less_than', 'empty', 'not_empty'];
  return ['equals', 'not_equals', 'contains', 'empty', 'not_empty'];
}

function defaultOperatorForParent(field: ScenarioIntakeField | null, dropdownOptions: DropdownOptionsByField): VisibilityConditionOperator {
  if (!field) return 'equals';
  return visibilityOperatorsForParent(field, dropdownOptions)[0] ?? 'equals';
}

function conditionValueOptions(fieldId: keyof BorrowerIntake, dropdownOptions: DropdownOptionsByField) {
  return (dropdownOptions[fieldId] ?? []).filter((option) => option.value);
}

function operatorNeedsNoValue(operator: VisibilityConditionOperator) {
  return operator === 'empty' || operator === 'not_empty';
}

function visibilityOperatorLabel(operator: VisibilityConditionOperator) {
  const labels: Record<VisibilityConditionOperator, string> = {
    equals: 'Equals',
    not_equals: 'Does not equal',
    any_of: 'Any of',
    contains: 'Contains',
    greater_than: 'Greater than',
    less_than: 'Less than',
    empty: 'Is empty',
    not_empty: 'Is not empty',
  };
  return labels[operator];
}

function normalizePipelineRequirement(field: ScenarioIntakeField): ScenarioIntakeField {
  return { ...field, pipelineRequired: field.required, required: minimumStartFields.includes(field.fieldId) } as ScenarioIntakeField;
}

function visibleConfiguredRequiredFields(sections: Array<{ step: QuoteIntakeStepDefinition; fields: ScenarioIntakeField[] }>): PipelineRequiredField[] {
  const seen = new Set<string>();
  return sections
    .flatMap(({ fields }) => fields)
    .filter((field): field is PipelineRequiredField => {
      const fieldId = String(field.fieldId);
      if (!isBorrowerIntakeField(fieldId) || !fieldPipelineRequired(field) || seen.has(fieldId)) return false;
      seen.add(fieldId);
      return true;
    });
}

function missingConfiguredRequiredFields(fields: PipelineRequiredField[], values: BorrowerIntake) {
  return fields.filter((field) => !(values[field.fieldId] ?? '').trim());
}

function fieldPipelineRequired(field: ScenarioIntakeField) {
  const record = field as ScenarioIntakeField & { pipelineRequired?: boolean };
  return record.pipelineRequired ?? field.required;
}

function isBorrowerIntakeField(fieldId: string): fieldId is keyof BorrowerIntake {
  return (loanPassQuoteIntakeFields as string[]).includes(fieldId);
}

function hasAnyValue(values: BorrowerIntake) {
  return Object.values(values).some((value) => value.trim());
}

function fieldForId(sections: Array<{ step: QuoteIntakeStepDefinition; fields: ScenarioIntakeField[] }>, fieldId: keyof BorrowerIntake) {
  return sections.flatMap(({ fields }) => fields).find((field) => field.fieldId === fieldId);
}

function fallbackDropdownConfigState(message: string): DropdownConfigState {
  return { kind: 'fallback', options: fallbackTenantDropdownOptions(), message };
}

function fallbackTenantDropdownOptions(): TenantDropdownOptions {
  const staticFilters = availableFiltersFor(tenantHomePreviewProducts);
  return {
    productTypes: toOptions(staticFilters.productTypes),
    investors: toOptions(staticFilters.investors),
    channels: toOptions(staticFilters.channels),
    loanPassEnums: loanPassEnumDropdownOptions(),
    source: 'fallback',
  };
}

function mergeTenantDropdownOptions(options: TenantDropdownOptions): TenantDropdownOptions {
  const fallback = fallbackTenantDropdownOptions();
  return {
    productTypes: options.productTypes.length > 0 ? options.productTypes : fallback.productTypes,
    investors: options.investors.length > 0 ? options.investors : fallback.investors,
    channels: options.channels.length > 0 ? options.channels : fallback.channels,
    loanPassEnums: { ...fallback.loanPassEnums, ...options.loanPassEnums },
    source: options.source,
  };
}

function dropdownOptionsForFields(options: TenantDropdownOptions | null): DropdownOptionsByField {
  const resolved = options ?? fallbackTenantDropdownOptions();
  return {
    mortgageType: withPlaceholder(resolved.productTypes, 'Select mortgage type'),
    investorCode: withPlaceholder(resolved.investors, 'Select investor'),
    channel: withPlaceholder(resolved.channels, 'Select channel'),
    channelCode: withPlaceholder(resolved.channels, 'Select channel'),
    propertyType: withPlaceholder(resolved.loanPassEnums.propertyType ?? [], 'Select property type'),
    occupancyType: withPlaceholder(resolved.loanPassEnums.occupancyType ?? [], 'Select occupancy type'),
    documentationType: withPlaceholder(resolved.loanPassEnums.documentationType ?? [], 'Select documentation type'),
    incomeDocumentationType: withPlaceholder(resolved.loanPassEnums.incomeDocumentationType ?? [], 'Select income documentation type'),
    citizenshipType: withPlaceholder(resolved.loanPassEnums.citizenshipType ?? [], 'Select citizenship type'),
    desiredAmortizationType: withPlaceholder(resolved.loanPassEnums.desiredAmortizationType ?? [], 'Select amortization type'),
    amortizationType: withPlaceholder(resolved.loanPassEnums.amortizationType ?? [], 'Select amortization type'),
    loanTermType: withPlaceholder(resolved.loanPassEnums.loanTermType ?? [], 'Select loan term type'),
    lockPeriodType: withPlaceholder(resolved.loanPassEnums.lockPeriodType ?? [], 'Select lock period'),
    channelType: withPlaceholder(resolved.loanPassEnums.channelType ?? [], 'Select channel type'),
    lienPosition: withPlaceholder(resolved.loanPassEnums.lienPosition ?? [], 'Select lien position'),
    propertyInformationType: withPlaceholder(resolved.loanPassEnums.propertyInformationType ?? [], 'Select property information type'),
    transactionType: withPlaceholder(resolved.loanPassEnums.transactionType ?? [], 'Select transaction type'),
  };
}

function availableFiltersForDropdownConfig(options: TenantDropdownOptions | null) {
  const fallback = availableFiltersFor(tenantHomePreviewProducts);
  return {
    productTypes: optionValues(options?.productTypes).length > 0 ? optionValues(options?.productTypes) : fallback.productTypes,
    investors: optionValues(options?.investors).length > 0 ? optionValues(options?.investors) : fallback.investors,
    channels: optionValues(options?.channels).length > 0 ? optionValues(options?.channels) : fallback.channels,
  };
}

function loanPassEnumDropdownOptions(): Partial<Record<keyof BorrowerIntake, DropdownOption[]>> {
  return {
    propertyType: toOptions(['Single Family', 'Condominium', 'Condotel', 'Two to Four Family', 'Manufactured Home', 'PUD', 'Multi-Family', 'Cooperative', 'Townhouse', 'Modular Home', 'Mixed-Use']),
    occupancyType: toOptions(['Investment', 'Primary Residence', 'Second Home']),
    documentationType: toOptions(['DSCR', 'Full Documentation', 'Bank Statements', '1099', 'Profit and Loss', 'WVOE Only', 'Asset Utilization', 'ATR-In-Full', 'K-1 Only', 'W-2', 'VOE']),
    incomeDocumentationType: toOptions(['DSCR', 'Full Documentation', 'Bank Statements', '1099', 'Profit and Loss', 'WVOE Only', 'Asset Utilization', 'ATR-In-Full', 'K-1 Only', 'W-2', 'VOE']),
    citizenshipType: toOptions(['US Citizen', 'Permanent Resident', 'Non-Permanent Resident', 'Foreign National', 'ITIN', 'DACA']),
    desiredAmortizationType: toOptions(['Fixed', 'Adjustable Rate']),
    amortizationType: toOptions(['Fixed', 'Adjustable Rate']),
    loanTermType: toOptions(['10 Year', '15 Year', '20 Year', '25 Year', '30 Year', '40 Year']),
    lockPeriodType: toOptions(['15 Days', '30 Days', '45 Days', '60 Days', '90 Days']),
    channelType: toOptions(['Retail', 'Wholesale', 'Correspondent', 'TPO', 'Consumer Direct']),
    lienPosition: toOptions(['First', 'Second']),
    propertyInformationType: toOptions(['Subject Property', 'Investment Property', 'Second Home']),
    transactionType: toOptions(['Purchase', 'Rate/Term Refinance', 'Cash-Out Refinance']),
  };
}

function toOptions(values: string[]): DropdownOption[] {
  return values.filter(Boolean).map((value) => ({ value, label: friendlyLabel(value) }));
}

function withPlaceholder(options: DropdownOption[], label: string): DropdownOption[] {
  const nonEmpty = options.filter((option) => option.value);
  return [{ value: '', label }, ...nonEmpty];
}

function withCurrentOption(options: DropdownOption[], value: string): DropdownOption[] {
  if (!value || options.some((option) => option.value === value)) return options;
  return [...options, { value, label: friendlyLabel(value) }];
}

function optionValues(options: DropdownOption[] | undefined): string[] {
  return (options ?? []).map((option) => option.value).filter(Boolean);
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

function isProductEligible(product: AuthorizedProduct) {
  return product.status === 'ACTIVE';
}

function productAvailabilityLabel(product: AuthorizedProduct) {
  return isProductEligible(product) ? 'Eligible product' : `${friendlyLabel(product.status)} product - row actions unavailable`;
}

function productUnavailableReason(product: AuthorizedProduct) {
  return isProductEligible(product) ? 'Product is eligible for row actions.' : `Product status is ${friendlyLabel(product.status)}.`;
}

function actionLabel(action: ProductRowAction) {
  if (action === 'refresh') return 'pricing refresh';
  if (action === 'lock') return 'lock request';
  return 'status review';
}

function lockFieldBindingsForMetadata(metadata: ScenarioIntakeMetadata | null, values: BorrowerIntake): LockFieldBinding[] {
  const settings = metadata?.settings?.lockingFields;
  if (!settings) return [];
  const fieldIds = [
    ['Request date', stringSetting(settings.requestDateFieldId) ?? stringSetting(settings.lockRequestDateFieldId)],
    ['Expiration date', stringSetting(settings.expirationDateFieldId) ?? stringSetting(settings.lockExpirationDateFieldId)],
    ['Extension', stringSetting(settings.extensionFieldId) ?? stringSetting(settings.extensionDaysFieldId) ?? stringSetting(settings.primaryExtensionFieldId)],
  ] as const;
  return fieldIds
    .filter((entry): entry is readonly [string, string] => Boolean(entry[1]))
    .map(([label, configuredFieldId]) => {
      const borrowerField = borrowerFieldForConfiguredId(configuredFieldId);
      return { label, configuredFieldId, value: borrowerField ? values[borrowerField] : '' };
    });
}

function autoConfirmationApprovalForMetadata(metadata: ScenarioIntakeMetadata | null) {
  const settings = metadata?.settings?.autoConfirmation;
  if (!settings) return { required: false, path: '' };
  const disabledActions = ['lock', 'reprice', 'relock', 'cancellation'].filter((key) => settings[key] === false);
  const approvalPath = stringSetting(settings.approvalPath) ?? stringSetting(settings.disabledApprovalPath);
  return {
    required: disabledActions.length > 0,
    path: approvalPath ? `Approval path: ${approvalPath}.` : 'Approval path is configured outside this UI slice.',
  };
}

function borrowerFieldForConfiguredId(configuredFieldId: string): keyof BorrowerIntake | null {
  const normalizedFieldId = normalized(configuredFieldId);
  const direct = loanPassQuoteIntakeFields.find((fieldId) => normalized(fieldId) === normalizedFieldId);
  if (direct) return direct;
  const aliases: Record<string, keyof BorrowerIntake> = {
    fieldlockrequestdate: 'effectiveDate',
    lockrequestdate: 'effectiveDate',
    requestdate: 'effectiveDate',
    fieldlockexpirationdate: 'requestedLockPeriodDays',
    ledgerlockexpirationdate: 'requestedLockPeriodDays',
    calclockexpirationdate: 'requestedLockPeriodDays',
    lockexpirationdate: 'requestedLockPeriodDays',
    expirationdate: 'requestedLockPeriodDays',
    fieldlockextension: 'lockExtension',
    lockextension: 'lockExtension',
    fieldlockextension2: 'lockExtension2',
    secondarylockextension: 'lockExtension2',
    fieldsecondarylockextension: 'lockExtension2',
  };
  return aliases[normalizedFieldId] ?? null;
}

function stringSetting(value: unknown) {
  return typeof value === 'string' && value.trim() ? value.trim() : null;
}

function priceScenarioColumnsForMetadata(metadata: ReturnType<typeof normalizeMetadataState>['metadata']): PriceScenarioColumn[] {
  const tableSettings = metadata?.settings?.priceScenarioTable;
  const hiddenColumns = new Set((tableSettings?.hiddenColumns ?? []).map(normalizedColumnKey).filter(Boolean));
  const configuredColumns = Array.isArray(tableSettings?.columns) ? tableSettings.columns : [];
  const configured = configuredColumns
    .map((entry, index) => normalizeConfiguredColumn(entry, index, metadata))
    .filter((column): column is PriceScenarioColumn & { order: number } => Boolean(column))
    .filter((column) => !hiddenColumns.has(column.key));
  const extraColumns = (tableSettings?.extraColumns ?? [])
    .map((entry, index) => normalizeConfiguredColumn(entry, configuredColumns.length + index, metadata))
    .filter((column): column is PriceScenarioColumn & { order: number } => Boolean(column))
    .filter((column) => !hiddenColumns.has(column.key));
  const defaultSource = defaultPriceScenarioColumns.filter((column) => !hiddenColumns.has(column.key));
  const source = configured.length > 0
    ? [...configured, ...extraColumns].sort((left, right) => left.order - right.order)
    : [...defaultSource.filter((column) => column.key !== 'actions'), ...extraColumns, ...defaultSource.filter((column) => column.key === 'actions')];
  const orderedKeys = (tableSettings?.columnOrder ?? []).map(normalizedColumnKey).filter(Boolean) as string[];
  const ordered = orderedKeys.length > 0
    ? [...source].sort((left, right) => orderIndex(left.key, orderedKeys) - orderIndex(right.key, orderedKeys))
    : source;
  return ensureActionColumn(ordered.map(({ key, label, fieldId, missingLabel, blockedLabel }) => ({ key, label, fieldId, missingLabel, blockedLabel })));
}

function normalizeConfiguredColumn(entry: string | { key?: string; fieldId?: string; label?: string; hidden?: boolean; visible?: boolean; order?: number; missingLabel?: string; blockedLabel?: string }, index: number, metadata?: ReturnType<typeof normalizeMetadataState>['metadata']): (PriceScenarioColumn & { order: number }) | null {
  const rawKey = typeof entry === 'string' ? entry : entry.key ?? entry.fieldId ?? '';
  const key = normalizedColumnKey(rawKey);
  if (!key) return null;
  if (typeof entry !== 'string' && (entry.hidden || entry.visible === false)) return null;
  if (isCalculationFieldId(key) && !calculationFieldVisible(key, metadata)) return null;
  const fallback = defaultPriceScenarioColumns.find((column) => column.key === key);
  const pipelineField = pipelineFieldConfig(metadata, key);
  return {
    key,
    fieldId: isCalculationFieldId(key) ? key : undefined,
    label: typeof entry === 'string' ? fallback?.label ?? pipelineField?.label ?? friendlyLabel(entry) : entry.label ?? fallback?.label ?? pipelineField?.label ?? friendlyLabel(rawKey),
    missingLabel: typeof entry === 'string' ? pipelineField?.missingLabel : entry.missingLabel ?? pipelineField?.missingLabel,
    blockedLabel: typeof entry === 'string' ? pipelineField?.blockedLabel : entry.blockedLabel ?? pipelineField?.blockedLabel,
    order: typeof entry === 'string' ? index : entry.order ?? index,
  };
}

function normalizedColumnKey(value: string): PriceScenarioColumnKey | null {
  const normalizedKey = normalized(value);
  const aliases: Record<string, BasePriceScenarioColumnKey> = {
    product: 'product', productname: 'product', productcode: 'product',
    investor: 'investor', investorcode: 'investor',
    status: 'status',
    adjustedrate: 'adjustedRate', rate: 'adjustedRate', noteRate: 'adjustedRate', noterate: 'adjustedRate',
    lockperiod: 'lockPeriod', requestedlockperiod: 'lockPeriod',
    adjustedprice: 'adjustedPrice', price: 'adjustedPrice',
    payment: 'payment', monthlypayment: 'payment',
    pitiaitia: 'pitiaItia', pitia: 'pitiaItia', itia: 'pitiaItia',
    dscr: 'dscr',
    ltv: 'ltv', loantovalue: 'ltv',
    fico: 'fico', creditscore: 'fico', decisioncreditscore: 'fico',
    actions: 'actions', action: 'actions',
  };
  if (aliases[normalizedKey]) return aliases[normalizedKey];
  return isCalculationFieldId(value) ? value.trim() : null;
}

function orderIndex(key: PriceScenarioColumnKey, orderedKeys: string[]) {
  const index = orderedKeys.indexOf(key);
  if (index !== -1) return index;
  const defaultIndex = defaultPriceScenarioColumns.findIndex((column) => column.key === key);
  return orderedKeys.length + (defaultIndex === -1 ? defaultPriceScenarioColumns.length : defaultIndex);
}

function ensureActionColumn(columns: PriceScenarioColumn[]) {
  return columns.some((column) => column.key === 'actions') ? columns : [...columns, { key: 'actions', label: 'Actions' }];
}

function isCalculationFieldId(value: string) {
  return value.trim().toLowerCase().startsWith('calc@');
}

function pipelineFieldConfig(metadata: ReturnType<typeof normalizeMetadataState>['metadata'], fieldId: string) {
  const normalizedFieldId = normalized(fieldId);
  return metadata?.settings?.pipelineFields?.find((field) => normalized(field.fieldId) === normalizedFieldId) ?? null;
}

function calculationFieldVisible(fieldId: string, metadata: ReturnType<typeof normalizeMetadataState>['metadata']) {
  const pipelineField = pipelineFieldConfig(metadata, fieldId);
  if (pipelineField?.hidden || pipelineField?.visible === false) return false;
  const hiddenPermissionFields = [
    ...(metadata?.settings?.fieldPermissions?.hiddenFields ?? []),
    ...(metadata?.settings?.fieldPermissions?.deniedFields ?? []),
  ].map(normalized);
  if (hiddenPermissionFields.includes(normalized(fieldId))) return false;
  return true;
}

function priceScenarioGridTemplate(columns: PriceScenarioColumn[]) {
  return columns.map((column) => {
    if (column.key === 'product') return 'minmax(15rem, 1.45fr)';
    if (column.key === 'actions') return 'minmax(5rem, 0.6fr)';
    return 'minmax(7.25rem, 1fr)';
  }).join(' ');
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

function loadFieldEditorDraft(tenantId: string): FieldEditorDraft {
  if (typeof window === 'undefined') return emptyFieldEditorDraft();
  const raw = window.localStorage.getItem(fieldEditorDraftKey(tenantId));
  if (!raw) return emptyFieldEditorDraft();
  try {
    const parsed = JSON.parse(raw) as { field?: Partial<FieldEditorDraft>; fieldName?: string; fieldId?: string; fieldDescription?: string; dataType?: string; enumTypeId?: string; constraints?: Partial<Record<'precision' | 'style' | 'units' | 'minimum' | 'maximum', string>> };
    const source: Partial<FieldEditorDraft> = parsed.field ?? {
      fieldName: parsed.fieldName,
      fieldId: parsed.fieldId,
      fieldDescription: parsed.fieldDescription,
      dataType: parsed.dataType,
      enumTypeId: parsed.enumTypeId,
    };
    return {
      ...emptyFieldEditorDraft(),
      fieldName: String(source.fieldName ?? ''),
      fieldId: String(source.fieldId ?? ''),
      fieldDescription: String(source.fieldDescription ?? ''),
      dataType: String(source.dataType ?? 'string'),
      enumTypeId: String(source.enumTypeId ?? ''),
      precision: String(source.precision ?? parsed.constraints?.precision ?? ''),
      style: String(source.style ?? parsed.constraints?.style ?? ''),
      units: String(source.units ?? parsed.constraints?.units ?? ''),
      minimum: String(source.minimum ?? parsed.constraints?.minimum ?? ''),
      maximum: String(source.maximum ?? parsed.constraints?.maximum ?? ''),
    };
  } catch {
    return emptyFieldEditorDraft();
  }
}

function saveFieldEditorDraftRecord(tenantId: string, field: FieldEditorSerializedDraft) {
  if (typeof window === 'undefined') return;
  window.localStorage.setItem(fieldEditorDraftKey(tenantId), JSON.stringify({ tenantId, field, savedAt: new Date().toISOString() }));
}

function loadFormBuilderDraftOrder(tenantId: string): Record<string, string[]> {
  if (typeof window === 'undefined') return {};
  const raw = window.localStorage.getItem(formBuilderDraftOrderKey(tenantId));
  if (!raw) return {};
  try {
    const parsed = JSON.parse(raw) as { sections?: Record<string, string[]> };
    return Object.fromEntries(Object.entries(parsed.sections ?? {}).filter(([, value]) => Array.isArray(value)));
  } catch {
    return {};
  }
}

function saveFormBuilderDraftOrder(tenantId: string, sections: Record<string, string[]>) {
  if (typeof window === 'undefined') return;
  window.localStorage.setItem(formBuilderDraftOrderKey(tenantId), JSON.stringify({ tenantId, sections, savedAt: new Date().toISOString() }));
}

function loadFormBuilderVisibilityDrafts(tenantId: string): Record<string, VisibilityConditionDraft> {
  if (typeof window === 'undefined') return {};
  const raw = window.localStorage.getItem(formBuilderVisibilityKey(tenantId));
  if (!raw) return {};
  try {
    const parsed = JSON.parse(raw) as { conditions?: Record<string, VisibilityConditionDraft> };
    return Object.fromEntries(Object.entries(parsed.conditions ?? {}).filter(([, condition]) => isVisibilityConditionDraft(condition)));
  } catch {
    return {};
  }
}

function saveFormBuilderVisibilityDrafts(tenantId: string, conditions: Record<string, VisibilityConditionDraft>) {
  if (typeof window === 'undefined') return;
  window.localStorage.setItem(formBuilderVisibilityKey(tenantId), JSON.stringify({ tenantId, conditions, savedAt: new Date().toISOString() }));
}

function isVisibilityConditionDraft(value: unknown): value is VisibilityConditionDraft {
  const record = value as Partial<VisibilityConditionDraft>;
  return typeof record?.parentFieldId === 'string' && typeof record.operator === 'string' && Array.isArray(record.values);
}

function formBuilderDraftOrderKey(tenantId: string) {
  return `wcpe:application-form-builder:${normalized(tenantId)}:draft-order`;
}

function formBuilderVisibilityKey(tenantId: string) {
  return `wcpe:application-form-builder:${normalized(tenantId)}:conditional-visibility`;
}

function fieldEditorDraftKey(tenantId: string) {
  return `wcpe:application-form-builder:${normalized(tenantId)}:field-editor`;
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
