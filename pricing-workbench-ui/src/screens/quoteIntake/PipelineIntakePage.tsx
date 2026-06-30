import { type CSSProperties, type FocusEvent, type FormEvent, type MouseEvent, type ReactNode, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import type { OfferComparisonView, OfferSummary } from '../../lib/api/offers';
import { fetchActiveApplicationFormVersion, fetchTenantDropdownOptions, loanPassQuoteIntakeFields, type ApplicationFormRuntimeState, type BorrowerIntake, type DropdownOption, type LaunchState, type MetadataState, type QuickQuoteLosPrefillMapping, type QuoteLaunchSelectedProduct, type ScenarioIntakeField, type ScenarioIntakeMetadata, type TenantDropdownOptions } from '../../lib/api/quoteRuns';
import { availableFiltersFor, tenantHomePreviewProducts, type AuthorizedProduct, type TenantProductsResponse, type TenantProductStatus } from '../../lib/api/tenantHome';
import { createDraftScenario, getDraftScenario, loadDraftBackup, saveDraftBackup, updateDraftScenario, type DraftBackup, type DraftScenario } from './draft';
import { emptyFieldEditorDraft, fieldEditorDataTypeLabel, fieldEditorDataTypes, serializeFieldEditorDraft, validateFieldEditorDraft, type FieldEditorDraft, type FieldEditorSerializedDraft } from './fieldEditorDataTypes';
import { launchQuoteRun } from './launch';
import { fieldsForStep, isHeaderMetadataField, normalizeMetadataState, stepsForMetadata, type QuoteIntakeSection, type QuoteIntakeStepDefinition } from './metadata';
import { ResumeDraft, draftToBackup } from './ResumeDraft';
import { StepFields, type DropdownOptionsByField } from './steps/StepFields';
import { errorsToValidation, firstInvalidField, validateFields, type IntakeFieldErrors } from './validation';
import { businessFacingText } from '../../lib/utils/businessFacingText';
import './QuoteIntake.css';

type QuoteMode = 'pipeline' | 'quickquote';

export type PipelineIntakePageProps = {
  tenantId?: string;
  mode?: QuoteMode;
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

type LiveOfferState =
  | { kind: 'idle' }
  | { kind: 'loading'; runId: string }
  | { kind: 'loaded'; runId: string; comparison: OfferComparisonView }
  | { kind: 'unavailable'; runId: string; message: string };

type AutosaveSnapshot = {
  values: BorrowerIntake;
  saveDraft: (reason?: string, valueOverride?: BorrowerIntake) => Promise<DraftScenario | null>;
};

type BasePriceScenarioColumnKey = 'product' | 'investor' | 'status' | 'adjustedRate' | 'lockPeriod' | 'adjustedPrice' | 'payment' | 'pitiaItia' | 'dscr' | 'fees' | 'llpa' | 'ltv' | 'fico' | 'audit' | 'actions';
type PriceScenarioColumnKey = BasePriceScenarioColumnKey | string;

type PriceScenarioColumn = {
  key: PriceScenarioColumnKey;
  label: string;
  fieldId?: string;
  missingLabel?: string;
  blockedLabel?: string;
};

type ProductRowAction = 'refresh' | 'lock' | 'status';

const quickQuoteProductEvidenceActions = new Set([
  'pipeline-product-selected',
  'pipeline-row-selected',
  'pipeline-row-refresh-requested',
  'pipeline-row-lock-requested',
  'pipeline-row-status-reviewed',
]);

const modeToggleEvidenceActions = new Set([
  'quote-mode-active',
  'quote-mode-filter-reset',
  'quote-mode-switch-confirmation-required',
  'quote-mode-switched',
  'quote-mode-switch-cancelled',
  'quote-mode-switch-save-failed',
]);

const quickQuoteDraftEvidenceActions = new Set([
  'draft-created',
  'draft-updated',
  'draft-local-backup',
  'pipeline-retrieved',
]);

const quickQuoteComparisonEvidenceActions = new Set([
  'quickquote-comparison-selection-changed',
]);

type ProductRowActionState = {
  productCode: string;
  tone: 'ready' | 'attention' | 'blocked';
  message: string;
  details: string[];
};

type QuoteEconomicsMetricKey = 'rate' | 'price' | 'payment' | 'dscr' | 'fees' | 'llpa';

type ProductMetricTraceRef = {
  label: string;
  value: string;
  metric?: string;
  href?: string;
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
type SaveRetrieveIdentifierField = 'borrowerLastName' | 'loanNumber';

type LosOverrideAudit = {
  fieldId: keyof BorrowerIntake;
  label: string;
  originalValue: string;
  overrideValue: string;
  actor: string;
  changedAt: string;
  affectedOutputTraces: string[];
};

type QuickQuoteDraftContext = {
  storyId: 'PII-77-S07';
  sourceMode: QuoteMode;
  selectedProduct?: QuoteLaunchSelectedProduct;
  comparedProducts: QuoteLaunchSelectedProduct[];
  productFilters: ProductFilterState;
  quoteOutputs: Array<{ productCode: string; metric: QuoteEconomicsMetricKey; value: string; state: 'ready' | 'missing' | 'assumption'; traceRef?: ProductMetricTraceRef }>;
  visibleAssumptions: string[];
  traceReferences: ProductMetricTraceRef[];
  losPrefillMappings: QuickQuoteLosPrefillMapping[];
  savedAt: string;
};

type ModeSwitchRequest = {
  mode: QuoteMode;
  route: string;
};


const tenantBoundaryPlaceholder = 'ui-preview-tenant';
const localUnsyncedDraftId = 'local-unsynced-pipeline-draft';
const lastLiveQuoteRunStoragePrefix = 'loanweft:lastLiveQuoteRunId:';
const liveQuoteOffersStoragePrefix = 'loanweft:liveQuoteOffers:';
const selectedOfferStoragePrefix = 'loanweft:selectedOfferId:';
const tenantDropdownConfigCache = new Map<string, DropdownConfigState>();
const saveRetrieveIdentifierFields: SaveRetrieveIdentifierField[] = ['borrowerLastName', 'loanNumber'];
const minimumStartFields: Array<keyof BorrowerIntake> = [];
const minimumQuoteFields: Array<keyof BorrowerIntake> = ['mortgageType', 'channel', 'loanPurpose', 'decisionCreditScore', 'baseLoanAmount'];
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

// Safe local QuickQuote fixture values are taken from existing LoanHouse/LoanPASS
// validation fixtures and UI tests. Borrower last name and loan number remain
// empty because they are Save/Retrieve keys, not pricing requirements.
const quickQuoteSafeScenarioDefaults: Partial<Record<keyof BorrowerIntake, string>> = {
  channel: 'RETAIL',
  channelCode: 'RETAIL',
  loanPurpose: 'Purchase',
  mortgageType: 'Conventional',
  decisionCreditScore: '720',
  baseLoanAmount: '425000',
  loanToValue: '80',
  desiredLoanTerm: '360',
  desiredAmortizationType: 'Fixed',
  state: 'CA',
  zip: '90001',
  propertyType: 'Single Family',
  occupancyType: 'Primary Residence',
  totalBorrowerIncome: '12000',
  monthlyDebt: '2500',
};

const quickQuoteSafeProductFilters: ProductFilterState = {
  ...emptyProductFilters,
  ltvMin: '75',
  ltvMax: '95',
  ficoMin: '720',
  ficoMax: '760',
  loanAmountMin: '350000',
  loanAmountMax: '625000',
  term: '360',
  propertyType: 'Single Family',
  occupancy: 'Primary Residence',
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

function initialIntakeForMode(mode: QuoteMode): BorrowerIntake {
  return mode === 'quickquote' ? { ...initialQuoteIntake, ...quickQuoteSafeScenarioDefaults } : initialQuoteIntake;
}

export function PipelineIntakePage({
  tenantId = tenantBoundaryPlaceholder,
  mode = 'pipeline',
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
  const [localIntake, setLocalIntake] = useState<BorrowerIntake>(() => intake ?? initialIntakeForMode(mode));
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
  const [productFilters, setProductFilters] = useState<ProductFilterState>(() => mode === 'quickquote' ? quickQuoteSafeProductFilters : emptyProductFilters);
  const [liveOfferState, setLiveOfferState] = useState<LiveOfferState>(() => loadCachedLiveOfferState(tenantId));
  const [selectedLiveOfferId, setSelectedLiveOfferId] = useState<string>('');
  const [selectedProduct, setSelectedProduct] = useState<AuthorizedProduct | null>(null);
  const [productDetailOpen, setProductDetailOpen] = useState(false);
  const [comparisonProductCodes, setComparisonProductCodes] = useState<string[]>(() => tenantHomePreviewProducts.slice(0, 2).map((product) => product.productCode));
  const [rowActionState, setRowActionState] = useState<ProductRowActionState | null>(null);
  const [dropdownConfigState, setDropdownConfigState] = useState<DropdownConfigState>(() => tenantDropdownConfigCache.get(tenantId) ?? { kind: 'loading', options: fallbackTenantDropdownOptions(), message: 'Loading tenant dropdown options...' });
  const [saveOpen, setSaveOpen] = useState(false);
  const [saveKeys, setSaveKeys] = useState({ borrowerLastName: '', loanNumber: '' });
  const [saveError, setSaveError] = useState('');
  const [saveMissingFields, setSaveMissingFields] = useState<SaveRetrieveIdentifierField[]>([]);
  const [retrieveOpen, setRetrieveOpen] = useState(false);
  const [retrieveKeys, setRetrieveKeys] = useState({ borrowerLastName: '', loanNumber: '' });
  const [retrieveError, setRetrieveError] = useState('');
  const [retrieveLoading, setRetrieveLoading] = useState(false);
  const [hasUnsavedModeChanges, setHasUnsavedModeChanges] = useState(false);
  const [pendingModeSwitch, setPendingModeSwitch] = useState<ModeSwitchRequest | null>(null);
  const [builderDraftOrder, setBuilderDraftOrder] = useState<Record<string, string[]>>(() => loadFormBuilderDraftOrder(tenantId));
  const [builderVisibilityDrafts, setBuilderVisibilityDrafts] = useState<Record<string, VisibilityConditionDraft>>(() => loadFormBuilderVisibilityDrafts(tenantId));
  const [builderValidationMessage, setBuilderValidationMessage] = useState('');
  const [fieldEditorDraft, setFieldEditorDraft] = useState<FieldEditorDraft>(() => loadFieldEditorDraft(tenantId));
  const [fieldEditorValidationMessage, setFieldEditorValidationMessage] = useState('');
  const [fieldEditorSavedSummary, setFieldEditorSavedSummary] = useState('');
  const [applicationFormRuntimeState, setApplicationFormRuntimeState] = useState<ApplicationFormRuntimeState | { kind: 'loading'; message: string }>(() => ({ kind: 'loading', message: 'Loading application fields...' }));
  const [validationBannerDismissed, setValidationBannerDismissed] = useState(false);
  const [losOverrideAudit, setLosOverrideAudit] = useState<Record<string, LosOverrideAudit>>(() => overrideAuditFromClientContext((intake ?? initialQuoteIntake).clientContext));
  const savingRef = useRef(false);
  const savePromiseRef = useRef<Promise<DraftScenario | null> | null>(null);
  const autosaveSnapshotRef = useRef<AutosaveSnapshot | null>(null);
  const firstEntryDraftRef = useRef(false);
  const validationBannerSignatureRef = useRef('');
  const validationBannerFocusKeyRef = useRef('');
  const values = useMemo(() => normalizeDisplayedIntake(localIntake), [localIntake]);
  const mergedErrors = { ...errors, ...localErrors };
  const { metadata } = normalizeMetadataState(metadataState);
  const runtimeMetadata = applicationFormRuntimeState.kind === 'loaded' ? applicationFormRuntimeState.metadata : metadata;
  const orderedSteps = useMemo(() => stepsForMetadata(runtimeMetadata), [runtimeMetadata]);
  const compatibilityStartFieldIds = useMemo(() => startFieldIdsForMetadata(runtimeMetadata, values), [runtimeMetadata, values]);
  const showProgressiveCompatibility = compatibilityStartFieldIds.some((fieldId) => fieldId === 'quoteIntent');
  const sections = useMemo(() => consolidateCanonicalFields(orderedSteps.map((step) => ({ step, fields: fieldsForStep(runtimeMetadata, step.id).map(normalizePipelineRequirement) }))), [orderedSteps, runtimeMetadata]);
  const runtimeSections = useMemo(() => applyRuntimeFieldVisibility(sections, values, {}), [sections, values]);
  const draftId = draftIdFromLocation();
  const fieldDropdownOptions = useMemo(() => dropdownOptionsForFields(dropdownConfigState.options), [dropdownConfigState.options]);
  const liveOffers = liveOfferState.kind === 'loaded' ? liveOfferState.comparison.offers : [];
  const liveFilteredOffers = useMemo(() => filterLiveOffers(liveOffers, productFilters), [liveOffers, productFilters]);
  const liveFilterOptions = useMemo(() => availableFiltersForOffers(liveOffers), [liveOffers]);
  const availableFilters = useMemo(() => liveOffers.length > 0 ? liveFilterOptions : availableFiltersForDropdownConfig(dropdownConfigState.options), [dropdownConfigState.options, liveFilterOptions, liveOffers.length]);
  const activeFilterCount = Object.values(productFilters).filter((value) => value.trim()).length;
  const filteredProducts = useMemo(() => filterProducts(tenantHomePreviewProducts, productFilters), [productFilters]);
  const productGridStatusCounts = useMemo(() => {
    const counts: Record<TenantProductStatus, number> = { ACTIVE: 0, PENDING: 0, INACTIVE: 0 };
    filteredProducts.forEach((product) => { counts[product.status] += 1; });
    return counts;
  }, [filteredProducts]);
  const productGridStatusSummary = [
    productGridStatusCounts.ACTIVE > 0 ? `${productGridStatusCounts.ACTIVE} active` : '',
    productGridStatusCounts.PENDING > 0 ? `${productGridStatusCounts.PENDING} pending` : '',
    productGridStatusCounts.INACTIVE > 0 ? `${productGridStatusCounts.INACTIVE} inactive` : '',
  ].filter(Boolean).join(' · ');
  const productGridCountSummary = liveOffers.length > 0
    ? liveFilteredOffers.length > 0 ? `${liveFilteredOffers.length} filtered of ${liveOffers.length} LoanHouse-backed offers` : `No matching LoanHouse offers of ${liveOffers.length} total`
    : `${filteredProducts.length > 0 ? `${filteredProducts.length} filtered` : 'No matching products'} of ${tenantHomePreviewProducts.length} total${productGridStatusSummary ? ` · ${productGridStatusSummary}` : ''}`;
  const priceScenarioColumns = useMemo(() => priceScenarioColumnsForMetadata(runtimeMetadata), [runtimeMetadata]);
  const lockFieldBindings = useMemo(() => lockFieldBindingsForMetadata(runtimeMetadata, values), [runtimeMetadata, values]);
  const autoConfirmationApproval = useMemo(() => autoConfirmationApprovalForMetadata(runtimeMetadata), [runtimeMetadata]);
  const configuredRequiredPricingFields = useMemo(() => visibleConfiguredRequiredFields(runtimeSections), [runtimeSections]);
  const quickQuotePricingInputFields = useMemo(() => quickQuotePricingFields(runtimeSections), [runtimeSections]);
  const requiredPricingFields = useMemo(() => mode === 'quickquote' ? mergeRequiredPricingFields(configuredRequiredPricingFields, quickQuotePricingInputFields) : configuredRequiredPricingFields, [configuredRequiredPricingFields, mode, quickQuotePricingInputFields]);
  const quickQuoteDisplayProduct = mode === 'quickquote' ? selectedProduct ?? tenantHomePreviewProducts.find(isProductEligible) ?? null : selectedProduct;
  const launchReadinessValues = useMemo(() => {
    if (mode !== 'quickquote' || !quickQuoteDisplayProduct) return values;
    return {
      ...values,
      channel: values.channel || quickQuoteDisplayProduct.channelCode,
      channelCode: values.channelCode || quickQuoteDisplayProduct.channelCode,
      investorCode: values.investorCode || quickQuoteDisplayProduct.investorCode,
      mortgageType: values.mortgageType || quickQuoteDisplayProduct.productType,
    };
  }, [mode, quickQuoteDisplayProduct, values]);
  const quickQuoteDisplayValues = useMemo(() => (mode === 'quickquote' ? launchReadinessValues : values), [launchReadinessValues, mode, values]);
  const missingRequiredFields = useMemo(() => missingConfiguredRequiredFields(requiredPricingFields, mode === 'quickquote' ? quickQuoteDisplayValues : values), [mode, quickQuoteDisplayValues, requiredPricingFields, values]);
  const launchMissingRequiredFields = useMemo(() => missingConfiguredRequiredFields(requiredPricingFields, launchReadinessValues), [requiredPricingFields, launchReadinessValues]);
  const actionableMissingRequiredFields = useMemo(() => (selectedProduct || submitAttempted ? missingRequiredFields : []), [missingRequiredFields, selectedProduct, submitAttempted]);
  const pipelineValidationErrors = useMemo<IntakeFieldErrors>(() => actionableMissingRequiredFields.reduce((acc, field) => {
    acc[field.fieldId] = `${field.label} needs attention.`;
    return acc;
  }, {} as IntakeFieldErrors), [actionableMissingRequiredFields]);
  const visibleErrors = useMemo(() => (submitAttempted || showProgressiveCompatibility || actionableMissingRequiredFields.length > 0 ? { ...pipelineValidationErrors, ...mergedErrors } : {}), [actionableMissingRequiredFields.length, mergedErrors, pipelineValidationErrors, showProgressiveCompatibility, submitAttempted]);
  const validationBannerSignature = actionableMissingRequiredFields.map((field) => String(field.fieldId)).join('|');
  const validationBannerVisible = validationBannerSignature.length > 0 && !validationBannerDismissed;
  const productGridStyle = { '--quote-product-grid-template': priceScenarioGridTemplate(priceScenarioColumns) } as CSSProperties;
  const quickQuoteColumns = useMemo<PriceScenarioColumn[]>(() => [
    { key: 'status', label: 'Eligibility' },
    { key: 'product', label: 'Product' },
    { key: 'adjustedRate', label: 'Rate' },
    { key: 'adjustedPrice', label: 'Price' },
    { key: 'payment', label: 'Payment' },
    { key: 'dscr', label: 'DSCR' },
    { key: 'fees', label: 'Fees' },
    { key: 'llpa', label: 'LLPA' },
    { key: 'audit', label: 'Review' },
    { key: 'actions', label: 'Actions' },
  ], []);
  const quickQuoteGridStyle = { '--quote-product-grid-template': priceScenarioGridTemplate(quickQuoteColumns) } as CSSProperties;
  const quickQuoteProductsVisible = mode !== 'quickquote' || submitAttempted || flowState.kind === 'created';
  const quickQuoteVisibleProducts = liveOffers.length > 0 ? tenantHomePreviewProducts : filteredProducts;
  const quickQuoteStatusCounts = useMemo(() => liveOffers.length > 0 ? offerStatusCounts(liveOffers) : productStatusCounts(filteredProducts), [filteredProducts, liveOffers]);
  const quickQuoteComparisonProducts = useMemo(() => tenantHomePreviewProducts.filter((product) => comparisonProductCodes.includes(product.productCode)), [comparisonProductCodes]);
  const losPrefillMappings = useMemo(() => quickQuotePrefillMappingsForMetadata(runtimeMetadata, runtimeSections), [runtimeMetadata, runtimeSections]);
  const losOverrideRows = useMemo(() => Object.values(losOverrideAudit).sort((left, right) => left.label.localeCompare(right.label)), [losOverrideAudit]);
  const losPrefillEditableFields = useMemo(() => fieldsById(Array.from(new Set(losPrefillMappings.map((mapping) => mapping.fieldId)))), [losPrefillMappings, runtimeSections]);
  const quickQuoteLoading = metadataState.kind === 'loading' || applicationFormRuntimeState.kind === 'loading' || dropdownConfigState.kind === 'loading' || flowState.kind === 'submitting';
  const quickQuoteLaunchLoading = metadataState.kind === 'loading' || applicationFormRuntimeState.kind === 'loading' || flowState.kind === 'submitting';
  const quickQuoteBorrower = borrowerContextLabel(values);
  const quickQuoteLoan = loanContextLabel(values);
  const startReady = true;
  const quoteReady = launchMissingRequiredFields.length === 0 && (mode === 'quickquote' || Boolean(selectedProduct));
  const launchDisabled = flowState.kind === 'submitting' || !quoteReady;
  const launchStatus = quoteReady
    ? mode === 'quickquote' && !quickQuoteProductsVisible
      ? 'Submit details to view product options'
      : selectedProduct || mode !== 'quickquote'
      ? 'Ready to launch quote'
      : 'Ready to launch quote with unsynced product context'
    : selectedProduct && launchMissingRequiredFields.length > 0
      ? 'Data required before pricing refresh'
      : mode === 'quickquote'
        ? 'Complete required fields to launch quote'
        : 'Select a product and complete required fields';
  const completionPercent = completedFactPercent(values);

  useEffect(() => {
    capture('quote-mode-active', modeSwitchAuditDetail(mode, 'active'));
    if (mode === 'quickquote') {
      setProductFilters(quickQuoteSafeProductFilters);
      capture('quote-mode-filter-reset', modeSwitchAuditDetail('quickquote', 'discard-pipeline-filters'));
    }
  }, [mode]);

  useEffect(() => {
    if (intake) setLocalIntake(intake);
  }, [intake]);

  useEffect(() => {
    setLiveOfferState(loadCachedLiveOfferState(tenantId));
    const cachedRunId = loadCachedLiveRunId(tenantId);
    if (cachedRunId) void loadLiveOffers(cachedRunId, { silent: true });
  }, [tenantId]);

  useEffect(() => {
    setBuilderDraftOrder(loadFormBuilderDraftOrder(tenantId));
    setBuilderVisibilityDrafts(loadFormBuilderVisibilityDrafts(tenantId));
    setBuilderValidationMessage('');
    setFieldEditorDraft(loadFieldEditorDraft(tenantId));
    setFieldEditorValidationMessage('');
    setFieldEditorSavedSummary('');
  }, [tenantId]);

  useEffect(() => {
    if (!validationBannerSignature) {
      validationBannerSignatureRef.current = '';
      validationBannerFocusKeyRef.current = '';
      setValidationBannerDismissed(false);
      return;
    }
    if (validationBannerSignatureRef.current !== validationBannerSignature) {
      validationBannerSignatureRef.current = validationBannerSignature;
      setValidationBannerDismissed(false);
    }
  }, [validationBannerSignature]);

  useLayoutEffect(() => {
    if (!validationBannerVisible) return;
    const firstField = actionableMissingRequiredFields[0];
    if (!firstField || validationBannerFocusKeyRef.current === validationBannerSignature) return;
    const tryFocus = () => {
      if (focusPipelineField(firstField.fieldId)) validationBannerFocusKeyRef.current = validationBannerSignature;
    };
    tryFocus();
    window.setTimeout(tryFocus, 0);
  }, [actionableMissingRequiredFields, dropdownConfigState.kind, validationBannerSignature, validationBannerVisible]);

  useEffect(() => {
    let cancelled = false;
    setApplicationFormRuntimeState({ kind: 'loading', message: 'Loading application fields...' });
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
        const fallbackState = fallbackDropdownConfigState('Some product choices are unavailable. Standard options are in use.');
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
    autosaveSnapshotRef.current = { values, saveDraft };
  });

  useEffect(() => {
    const timer = window.setInterval(() => {
      const snapshot = autosaveSnapshotRef.current;
      if (snapshot && hasAnyValue(snapshot.values)) void snapshot.saveDraft('autosave-interval', snapshot.values);
    }, 30_000);
    return () => window.clearInterval(timer);
  }, [scenarioId, scenarioVersion]);

  function changeField(field: keyof BorrowerIntake, value: string, options: { autoSave?: boolean } = {}) {
    trackLosOverride(field, value);
    const nextValues = { ...values, [field]: value };
    setLocalIntake(nextValues);
    setHasUnsavedModeChanges(true);
    setLocalErrors((current) => ({ ...current, [field]: undefined }));
    onChange?.(field, value);
    if (options.autoSave !== false && !firstEntryDraftRef.current && isLoanBasicsField(field) && value.trim()) {
      firstEntryDraftRef.current = true;
      void saveDraft('first-field-entry', nextValues);
    }
  }

  function trackLosOverride(field: keyof BorrowerIntake, value: string) {
    const mapping = losPrefillMappings.find((candidate) => candidate.fieldId === field);
    if (!mapping) return;
    const originalValue = (values[field] ?? '').trim();
    const overrideValue = value.trim();
    if (!originalValue || originalValue === overrideValue) return;
    const now = new Date().toISOString();
    setLosOverrideAudit((current) => ({
      ...current,
      [field]: {
        fieldId: field,
        label: mapping.wcpeField || String(field),
        originalValue,
        overrideValue,
        actor: values.actorId || 'current QuickQuote user',
        changedAt: now,
        affectedOutputTraces: mapping.affectedOutputTraces.length > 0 ? mapping.affectedOutputTraces : ['eligibility/pricing trace pending configured adapter'],
      },
    }));
  }

  function focusPipelineField(fieldId: keyof BorrowerIntake) {
    const control = document.getElementById(String(fieldId)) as HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement | null;
    if (!control || control.disabled) return false;
    control.focus();
    return document.activeElement === control;
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
    setHasUnsavedModeChanges(true);
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
    changeField('channel', channel, { autoSave: false });
    changeField('channelCode', product.channelCode, { autoSave: false });
    changeField('investorCode', product.investorCode, { autoSave: false });
    changeField('mortgageType', mortgageType, { autoSave: false });
    setSelectedProduct(product);
    setProductDetailOpen(false);
    setHasUnsavedModeChanges(true);
    setProductFilters((current) => ({ ...current, productType: product.productType, investor: product.investorCode, channel: product.channelCode, status: product.status }));
    setStatusMessage(`${product.productName} selected.`);
    capture('pipeline-product-selected', { productCode: product.productCode, investor: product.investorCode, channel: channelLabel(channel) });
  }

  function selectProduct(product: AuthorizedProduct) {
    setSelectedProduct(product);
    setProductDetailOpen(true);
    if (mode === 'quickquote') {
      setComparisonProductCodes((current) => current.includes(product.productCode) ? current : [...current, product.productCode]);
    }
    setHasUnsavedModeChanges(true);
    setStatusMessage(`${product.productName} row selected.`);
    capture('pipeline-row-selected', { productCode: product.productCode, status: product.status });
  }

  function toggleComparisonProduct(product: AuthorizedProduct) {
    setComparisonProductCodes((current) => current.includes(product.productCode) ? current.filter((code) => code !== product.productCode) : [...current, product.productCode]);
    setHasUnsavedModeChanges(true);
    setStatusMessage(`${product.productName} ${comparisonProductCodes.includes(product.productCode) ? 'removed from' : 'added to'} comparison.`);
    capture('quickquote-comparison-selection-changed', { productCode: product.productCode });
  }

  function requestRowAction(product: AuthorizedProduct, action: ProductRowAction) {
    if (action !== 'status' && !isProductEligible(product)) {
      setRowActionState({ productCode: product.productCode, tone: 'blocked', message: `${product.productName} is ${friendlyLabel(product.status)}; ${actionLabel(action)} is unavailable.`, details: [productUnavailableReason(product)] });
      return;
    }

    if (action === 'refresh') {
      setRowActionState({ productCode: product.productCode, tone: 'ready', message: `Pricing refresh queued for ${product.productName}.`, details: ['Use Launch Quote to send the current borrower facts to pricing.'] });
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
        details: lockFieldBindings.map((binding) => `${binding.label}: ${binding.value || 'Not yet captured'}`),
      });
      capture('pipeline-row-lock-requested', { productCode: product.productCode, approvalRequired: autoConfirmationApproval.required, lockFields: lockFieldBindings.map((binding) => binding.configuredFieldId) });
      return;
    }

    const traceDetails = productAuditTraceRefs(product, runtimeMetadata).map((ref) => `${ref.label}: ${ref.value}`);
    setRowActionState({ productCode: product.productCode, tone: isProductEligible(product) ? 'ready' : product.status === 'PENDING' ? 'attention' : 'blocked', message: `${product.productName} status: ${productEligibilityReviewLabel(product)}.`, details: [productUnavailableReason(product), ...(traceDetails.length > 0 ? traceDetails : ['Pricing trace is not available yet.'])] });
    capture('pipeline-row-status-reviewed', { productCode: product.productCode, status: product.status });
  }

  function clearFilters() {
    setHasUnsavedModeChanges(true);
    setProductFilters({ ...emptyProductFilters, status: '' });
    setStatusMessage('Filters cleared.');
  }

  function revealQuickQuoteProducts() {
    setSubmitAttempted(true);
    setStatusMessage('QuickQuote product options are ready for review.');
    capture('quickquote-products-revealed', { missingFields: missingRequiredFields.length, candidates: tenantHomePreviewProducts.length });
    const cachedRunId = loadCachedLiveRunId(tenantId);
    if (cachedRunId && liveOfferState.kind !== 'loaded' && liveOfferState.kind !== 'loading') void loadLiveOffers(cachedRunId);
  }

  async function loadLiveOffers(runId: string, options: { silent?: boolean } = {}) {
    setLiveOfferState({ kind: 'loading', runId });
    try {
      const comparison = await fetchLiveOfferComparison(tenantId, runId);
      const nextState: LiveOfferState = { kind: 'loaded', runId: comparison.runId || runId, comparison };
      setLiveOfferState(nextState);
      cacheLiveOfferState(tenantId, nextState);
      setSelectedLiveOfferId(comparison.selectedOfferId ?? '');
      if (!options.silent) setStatusMessage(`${comparison.offers.length} LoanHouse-backed offers loaded from the BFF.`);
      capture('live-bff-offers-loaded', { runId: comparison.runId || runId, offerCount: comparison.offers.length, status: comparison.status });
      return comparison;
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : 'BFF offers are unavailable.';
      setLiveOfferState({ kind: 'unavailable', runId, message });
      if (!options.silent) setStatusMessage(message);
      capture('live-bff-offers-unavailable', { runId, message });
      return null;
    }
  }

  function selectLiveOffer(runId: string, offer: OfferSummary) {
    setSelectedLiveOfferId(offer.offerId);
    try {
      window.sessionStorage.setItem(`${selectedOfferStoragePrefix}${runId}`, offer.offerId);
    } catch {
      // Session storage can be unavailable; visible selection remains in component state.
    }
    setStatusMessage(`${offer.productLabel ?? offer.offerId} selected for LoanHouse-backed offer workflow.`);
    capture('live-bff-offer-selected', { runId, offerId: offer.offerId, sourceRefs: sourceRefsForOffer(offer) });
  }

  function navigateLiveOfferDetail(runId: string, offer: OfferSummary) {
    selectLiveOffer(runId, offer);
    onNavigate?.(`/quote/${encodeURIComponent(runId)}/offers/${encodeURIComponent(offer.offerId)}`);
  }

  function navigateLiveOfferLock(runId: string, offer: OfferSummary) {
    selectLiveOffer(runId, offer);
    onNavigate?.(`/quote/${encodeURIComponent(runId)}/lock`);
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

  function openSavePipelineModal() {
    const nextKeys = identifierKeysFromValues(values);
    const missingFields = saveRetrieveIdentifierFields.filter((field) => !nextKeys[field].trim());
    setSaveKeys(nextKeys);
    setSaveMissingFields(missingFields);
    setSaveError('');
    if (missingFields.length > 0) {
      setSaveOpen(true);
      window.setTimeout(() => focusModalIdentifierField('save', missingFields[0]), 0);
      return;
    }
    void savePipeline({ ...values, ...nextKeys });
  }

  function submitSaveIdentifiers() {
    const validationError = firstMissingIdentifierError(saveKeys, saveMissingFields);
    if (validationError) {
      setSaveError(validationError.message);
      window.setTimeout(() => focusModalIdentifierField('save', validationError.field), 0);
      return;
    }
    const nextValues = { ...values, ...saveKeys };
    setSaveOpen(false);
    setSaveError('');
    void savePipeline(nextValues);
  }

  async function savePipeline(saveValues: BorrowerIntake) {
    let saved = await saveDraft('manual-save', saveValues);
    if (!saved) return;
    try {
      const loanBasics = sectionForStep(1);
      saved = await updateDraftScenario(tenantId, saved.scenarioId, saved.scenarioVersion, 'scenario-identity', backendDraftPayload(saveValues, loanBasics?.fields ?? []));
      setScenarioId(saved.scenarioId);
      setScenarioVersion(saved.scenarioVersion);
      saveDraftBackup(saved.scenarioId, saved.scenarioVersion, 1, saveValues);
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : 'Draft scenario save is temporarily unavailable.';
      setFlowState({ kind: 'outage', message });
      setStatusMessage(message);
      capture('pipeline-save-failed', { message });
      return;
    }
    savePipelineLookup(saveValues, saved.scenarioId, saved.scenarioVersion);
    setHasUnsavedModeChanges(false);
    setStatusMessage('Saved.');
  }

  async function retrievePipeline() {
    const borrowerLastName = retrieveKeys.borrowerLastName.trim();
    const loanNumber = retrieveKeys.loanNumber.trim();
    if (!borrowerLastName || !loanNumber) {
      setRetrieveError('Borrower Last Name and Loan Number are required.');
      window.setTimeout(() => focusModalIdentifierField('retrieve', borrowerLastName ? 'loanNumber' : 'borrowerLastName'), 0);
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
    setLosOverrideAudit(overrideAuditFromClientContext(resumedIntake.clientContext));
    restoreQuickQuoteDraftContext(resumedIntake);
    setLocalIntake(resumedIntake);
    Object.entries(resumedIntake).forEach(([field, value]) => {
      if (typeof value === 'string') onChange?.(field as keyof BorrowerIntake, value);
    });
    setRetrieveOpen(false);
    setRetrieveError('');
    setHasUnsavedModeChanges(false);
    setStatusMessage(message);
    capture('pipeline-retrieved', { scenarioId: draft.scenarioId, scenarioVersion: draft.scenarioVersion });
  }

  async function saveDraft(reason = 'manual-save', valueOverride?: BorrowerIntake): Promise<DraftScenario | null> {
    if (savingRef.current) return savePromiseRef.current;
    savingRef.current = true;
    const draftValues = withQuickQuoteDraftContext(withLosOverrideContext(valueOverride ?? values, losOverrideRows), {
      sourceMode: mode,
      selectedProduct,
      comparedProducts: mode === 'quickquote' ? quickQuoteComparisonProducts : filteredProducts,
      productFilters,
      missingFields: missingRequiredFields,
      metadata: runtimeMetadata,
      losPrefillMappings,
    });
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
        setHasUnsavedModeChanges(false);
        setStatusMessage(reason === 'quickquote-save-draft' ? 'QuickQuote draft saved to Pipeline context.' : 'Pipeline draft created and saved.');
        if (reason !== 'pre-launch') setFlowState({ kind: 'idle' });
        capture('draft-created', { scenarioId: draft.scenarioId, scenarioVersion: draft.scenarioVersion, reason });
        return draft;
      } else {
        const saved = await updateDraftScenario(tenantId, scenarioId ?? resumeBackup?.scenarioId ?? localUnsyncedDraftId, scenarioVersion || resumeBackup?.scenarioVersion || 1, 'scenario-identity', backendDraftPayload(draftValues, loanBasics?.fields ?? []));
        setScenarioId(saved.scenarioId);
        setScenarioVersion(saved.scenarioVersion);
        saveDraftBackup(saved.scenarioId, saved.scenarioVersion, 1, draftValues);
        setHasUnsavedModeChanges(false);
        setStatusMessage(reason === 'quickquote-save-draft' ? 'QuickQuote draft saved to Pipeline context.' : 'Pipeline draft auto-saved.');
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
    const productForLaunch = product ?? selectedProduct ?? (mode === 'quickquote' ? quickQuoteDisplayProduct : null);
    if (product) applyProduct(product);
    const launchValues = productForLaunch ? { ...values, channel: productForLaunch.channelCode, channelCode: productForLaunch.channelCode, investorCode: productForLaunch.investorCode, mortgageType: productForLaunch.productType } : values;
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
      let resolvedScenarioId = savedDraft?.scenarioId ?? scenarioId ?? resumeBackup?.scenarioId;
      let resolvedVersion = savedDraft?.scenarioVersion ?? (scenarioVersion || resumeBackup?.scenarioVersion || 1);
      if (!resolvedScenarioId && mode === 'quickquote') {
        resolvedScenarioId = localUnsyncedDraftId;
        resolvedVersion = 1;
        capture('quote-launch-local-draft-used', { reason: 'draft-persistence-unavailable' });
      }
      if (mode === 'quickquote' && productForLaunch && !selectedProduct) capture('quote-launch-local-product-context-used', { reason: 'catalog-or-explicit-selection-unavailable', productCode: productForLaunch.productCode });
      if (!resolvedScenarioId) throw new Error('Connected scenario draft is unavailable; quote launch requires backend draft support.');
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
      setFlowState({ kind: 'created', launch: launched.launch, selectedProduct: productForLaunch ? quoteLaunchSelectedProduct(productForLaunch) : undefined });
      capture('quote-launch-created', { runId: launched.launch.runId, nextRoute: launched.launch.nextRoute, productCode: productForLaunch?.productCode, uiTraceId: launched.launch.uiTraceId });
      if (launched.launch.runId) await loadLiveOffers(launched.launch.runId);
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
    setLosOverrideAudit(overrideAuditFromClientContext(resumedIntake.clientContext));
    restoreQuickQuoteDraftContext(resumedIntake);
    setLocalIntake(resumedIntake);
    Object.entries(resumedIntake).forEach(([field, value]) => {
      if (typeof value === 'string') onChange?.(field as keyof BorrowerIntake, value);
    });
    setResumeDismissed(true);
    setHasUnsavedModeChanges(false);
    setStatusMessage(`Draft ${resumeBackup.scenarioId} resumed.`);
  }

  function restoreQuickQuoteDraftContext(resumedIntake: BorrowerIntake) {
    const context = quickQuoteDraftContextFromClientContext(resumedIntake.clientContext);
    if (!context) return;
    setProductFilters(context.productFilters ?? emptyProductFilters);
    const productCode = context.selectedProduct?.productCode;
    const restoredProduct = productCode ? tenantHomePreviewProducts.find((product) => product.productCode === productCode) ?? null : null;
    setSelectedProduct(restoredProduct);
    setRowActionState(null);
  }

  function handleBlur(event: FocusEvent<HTMLFormElement>) {
    const target = event.target as unknown as HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement;
    if (target.name && target.value.trim()) void saveDraft('field-blur');
  }

  function requestModeSwitch(event: MouseEvent<HTMLAnchorElement>, nextMode: QuoteMode) {
    event.preventDefault();
    if (nextMode === mode) return;
    const request = { mode: nextMode, route: routeForMode(nextMode, selectedProduct) };
    if (hasUnsavedModeChanges) {
      setPendingModeSwitch(request);
      capture('quote-mode-switch-confirmation-required', modeSwitchAuditDetail(nextMode, 'unsaved-changes'));
      return;
    }
    finishModeSwitch(request, 'direct');
  }

  async function saveAndSwitchMode() {
    if (!pendingModeSwitch) return;
    const saved = await saveDraft('mode-switch-save');
    if (!saved) {
      setStatusMessage('Save was unavailable. Discard or keep editing before switching modes.');
      capture('quote-mode-switch-save-failed', modeSwitchAuditDetail(pendingModeSwitch.mode, 'save-failed'));
      return;
    }
    finishModeSwitch(pendingModeSwitch, 'save');
  }

  function discardAndSwitchMode() {
    if (!pendingModeSwitch) return;
    setHasUnsavedModeChanges(false);
    finishModeSwitch(pendingModeSwitch, 'discard');
  }

  function cancelModeSwitch() {
    if (pendingModeSwitch) capture('quote-mode-switch-cancelled', modeSwitchAuditDetail(pendingModeSwitch.mode, 'keep-editing'));
    setPendingModeSwitch(null);
  }

  function finishModeSwitch(request: ModeSwitchRequest, decision: 'direct' | 'save' | 'discard') {
    if (request.mode === 'quickquote') setProductFilters(emptyProductFilters);
    setPendingModeSwitch(null);
    capture('quote-mode-switched', modeSwitchAuditDetail(request.mode, decision));
    if (onNavigate) onNavigate(request.route);
    else window.location.assign(request.route);
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
    const detailFieldIds = Array.from(new Set<keyof BorrowerIntake>([...minimumQuoteFields, 'documentationType', 'state', 'zip']));
    return fieldsById(detailFieldIds).map((field) => ({
      ...field,
      required: minimumQuoteFields.includes(field.fieldId),
    }));
  }

  function capture(action: string, detail: Record<string, unknown>) {
    let storyId = 'PII-72-S04';
    if (modeToggleEvidenceActions.has(action)) storyId = 'PII-77-S05';
    if (quickQuoteDraftEvidenceActions.has(action)) storyId = 'PII-77-S07';
    if (mode === 'quickquote') storyId = quickQuoteProductEvidenceActions.has(action) ? 'PII-77-S02' : 'PII-77-S01';
    if (quickQuoteComparisonEvidenceActions.has(action)) storyId = 'PII-77-S06';
    if (modeToggleEvidenceActions.has(action)) storyId = 'PII-77-S05';
    if (quickQuoteDraftEvidenceActions.has(action)) storyId = 'PII-77-S07';
    onEvidenceCapture?.({ action, storyId, ...detail });
  }

  function modeSwitchAuditDetail(nextMode: QuoteMode, decision: string) {
    return {
      mode: nextMode,
      sourceMode: mode,
      sourceScenarioId: scenarioId ?? resumeBackup?.scenarioId ?? '',
      selectedProductCode: selectedProduct?.productCode ?? '',
      draftLinked: Boolean(scenarioId ?? resumeBackup?.scenarioId),
      decision,
    };
  }

  function renderModeSwitchDialog() {
    if (!pendingModeSwitch) return null;
    const target = pendingModeSwitch.mode === 'quickquote' ? 'QuickQuote' : 'Pipeline';
    return (
      <div className="quote-pipeline-modal" role="dialog" aria-modal="true" aria-labelledby="mode-switch-heading">
        <div className="quote-pipeline-modal__card">
          <div className="quote-pipeline-modal__header">
            <h3 id="mode-switch-heading">Switch to {target}?</h3>
            <button type="button" onClick={cancelModeSwitch} aria-label="Close">×</button>
          </div>
          <p className="quote-pipeline-modal__copy">Save this draft before switching, or discard local mode changes.</p>
          <div className="quote-pipeline-modal__actions">
            <button type="button" className="quote-filter-clear" onClick={cancelModeSwitch}>Keep editing</button>
            <button type="button" className="quote-filter-clear" onClick={discardAndSwitchMode}>Discard</button>
            <button type="button" className="quote-intake-primary" onClick={() => void saveAndSwitchMode()}>Save draft</button>
          </div>
        </div>
      </div>
    );
  }

  if (mode === 'quickquote') {
    return (
      <section id="quickquote-workspace" className="quickquote-workspace" aria-labelledby="quickquote-heading" data-loading={quickQuoteLoading ? 'true' : 'false'}>
        <header className="quickquote-header" aria-label="QuickQuote header">
          <div className="quickquote-header__identity">
            <span className="quickquote-header__state" aria-current="page">Current workspace</span>
            <h1 id="quickquote-heading">QuickQuote</h1>
          </div>
          <dl className="quickquote-context" aria-label="Borrower and loan context">
            <div><dt>Borrower</dt><dd>{quickQuoteBorrower}</dd></div>
            <div><dt>Loan</dt><dd>{quickQuoteLoan}</dd></div>
            <div><dt>Updated</dt><dd>{statusMessage ? 'Now' : 'Awaiting changes'}</dd></div>
          </dl>
        </header>

        <section className="quickquote-status-strip" aria-label="QuickQuote status strip">
          <MetricCard label="Pricing facts" value={`${Math.max(requiredPricingFields.length - missingRequiredFields.length, 0)}/${requiredPricingFields.length}`} detail="ready" tone={missingRequiredFields.length === 0 ? 'ready' : 'attention'} />
          {missingRequiredFields.length > 0 ? <MetricCard label="Missing" value={String(missingRequiredFields.length)} detail="facts" tone="attention" /> : <MetricCard label="Readiness" value="Ready" detail="pricing facts complete" tone="ready" />}
          <MetricCard label="Eligible" value={String(quickQuoteStatusCounts.eligible)} detail="products" tone="ready" />
          <MetricCard label="Refer" value={String(quickQuoteStatusCounts.refer)} detail="review" tone={quickQuoteStatusCounts.refer > 0 ? 'attention' : undefined} />
          <MetricCard label="Ineligible" value={String(quickQuoteStatusCounts.ineligible)} detail="products" />
          <button type="button" className="quote-intake-primary" onClick={revealQuickQuoteProducts} aria-controls="quickquote-products-panel" aria-expanded={quickQuoteProductsVisible}>Find Products</button>
        </section>

        <div className="quickquote-grid-shell">
          <aside className="quickquote-prefill-rail" aria-label="QuickQuote pricing input rail">
            <FilterCard title="Filters" eyebrow="Products" badge={activeFilterCount > 0 ? `${activeFilterCount} active` : undefined}>
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
            <QuickQuotePricingInputs fields={quickQuotePricingInputFields} values={quickQuoteDisplayValues} errors={visibleErrors} dropdownOptions={fieldDropdownOptions} dropdownLoading={dropdownConfigState.kind === 'loading'} selectedProduct={quickQuoteDisplayProduct} missingFields={missingRequiredFields} onChange={changeField} onFocusField={focusPipelineField} />
            <QuickQuotePrefillGroups sections={runtimeSections} values={quickQuoteDisplayValues} mappings={losPrefillMappings} overrides={losOverrideAudit} />
            <QuickQuoteMappedFieldOverrides fields={losPrefillEditableFields} values={quickQuoteDisplayValues} errors={visibleErrors} dropdownOptions={fieldDropdownOptions} dropdownLoading={dropdownConfigState.kind === 'loading'} onChange={changeField} />
            <QuickQuoteMissingFacts fields={missingRequiredFields} mappings={losPrefillMappings} values={quickQuoteDisplayValues} />
            <QuickQuoteOverrideAudit overrides={losOverrideRows} />
          </aside>

          <section id="quickquote-products-panel" className="quickquote-products" aria-labelledby="quickquote-products-heading" aria-busy={quickQuoteLoading ? 'true' : 'false'}>
            <div className="quickquote-products__toolbar">
              <div>
                <span className="eyebrow">Grid</span>
                <h2 id="quickquote-products-heading">Product eligibility</h2>
                <p className="quickquote-products__assurance">Submit details before product options are displayed. Pricing remains service-owned.</p>
              </div>
              <QuickQuoteStateCard title="Pricing" state={quickQuoteLoading ? 'loading' : liveOffers.length > 0 || tenantHomePreviewProducts.length > 0 ? 'ready' : 'empty'} detail={liveOffers.length > 0 ? `${liveOffers.length} LoanHouse BFF offers` : `${tenantHomePreviewProducts.length} preview candidates`} />
            </div>

            {quickQuoteLoading ? <QuickQuoteSkeleton /> : null}
            {!quickQuoteLoading && tenantHomePreviewProducts.length === 0 ? <QuickQuoteEmptyState missingFacts={missingRequiredFields.length} /> : null}

            {!quickQuoteProductsVisible ? <QuickQuoteSubmitGate missingFacts={missingRequiredFields.length} /> : liveOfferState.kind === 'loaded' ? (
              <LiveOfferGrid runId={liveOfferState.runId} offers={liveFilteredOffers} selectedOfferId={selectedLiveOfferId} onSelect={selectLiveOffer} onDetail={navigateLiveOfferDetail} onLock={navigateLiveOfferLock} />
            ) : (
              <div className="quote-product-grid quote-product-grid--quickquote" role="table" aria-label="QuickQuote product eligibility grid" style={quickQuoteGridStyle}>
                <div className="quote-product-row quote-product-row--header" role="row">
                  {quickQuoteColumns.map((column) => <div key={column.key} role="columnheader">{column.label}</div>)}
                </div>
                {quickQuoteVisibleProducts.map((product) => (
                  <ProductResultRow key={product.productCode} product={product} values={values} columns={quickQuoteColumns} missingFields={missingRequiredFields} metadata={runtimeMetadata} selected={selectedProduct?.productCode === product.productCode} comparisonSelected={comparisonProductCodes.includes(product.productCode)} actionState={rowActionState?.productCode === product.productCode ? rowActionState : null} onSelect={selectProduct} onApply={applyProduct} onAction={requestRowAction} onCompare={toggleComparisonProduct} />
                ))}
              </div>
            )}
            {quickQuoteProductsVisible && liveOfferState.kind === 'loading' ? <p className="quote-intake-status" role="status">Loading LoanHouse-backed BFF offers...</p> : null}
            {quickQuoteProductsVisible && liveOfferState.kind === 'unavailable' ? <p className="quote-intake-status" role="alert">{liveOfferState.message}</p> : null}

            {quickQuoteProductsVisible && liveOfferState.kind !== 'loaded' ? <QuickQuoteComparisonPanel products={quickQuoteComparisonProducts} selectedProductCode={selectedProduct?.productCode ?? ''} values={values} missingFields={missingRequiredFields} metadata={runtimeMetadata} onSelectProduct={selectProduct} onUseProduct={applyProduct} /> : null}
          </section>
        </div>

        <div className="quote-pipeline-launchbar quickquote-actions" aria-label="QuickQuote actions">
          <div>
            <strong>{selectedProduct?.productName ?? quickQuoteDisplayProduct?.productName ?? 'Quote launch'}</strong>
            <span id="quickquote-action-state">{quickQuoteLaunchLoading ? 'Loading quote state' : launchStatus}</span>
          </div>
          <button type="button" className="quote-intake-primary" disabled={launchDisabled || quickQuoteLaunchLoading} aria-describedby="quickquote-action-state" onClick={() => void launch()}>{flowState.kind === 'submitting' ? 'Launching...' : 'Launch quote'}</button>
        </div>

        {selectedProduct && productDetailOpen ? <ProductDetailPanel product={selectedProduct} values={values} metadata={runtimeMetadata} onBack={() => setProductDetailOpen(false)} onUse={applyProduct} /> : null}
        {renderModeSwitchDialog()}
      </section>
    );
  }

  return (
    <section id="borrower-intake" className="quote-intake-shell quote-intake-shell--product-finder quote-intake-shell--pipeline-redesign quote-intake-shell--single-page" aria-labelledby="intake-heading">
      <div className="quote-pipeline-topbar">
        <div>
          <p className="eyebrow">Pipeline</p>
          <h1>Progressive Quote Intake</h1>
          <nav className="quote-intake-status" aria-label="Available workbench modules">
            Progressive Quote Intake · Quote Comparison · Lock Management · Scenario Analysis · Tenant Onboarding · Product Management · Rate Sheet Intake · Pricing Analysis
          </nav>
          {showProgressiveCompatibility ? <p className="quote-intake-status">New prospect intake</p> : null}
          <h2 id="intake-heading">Intake</h2>
          <nav className="quickquote-mode-toggle" aria-label="Quote mode">
            <a href="/quote/start" onClick={(event) => requestModeSwitch(event, 'quickquote')}>QuickQuote</a>
            <a href="/pipeline" aria-current="page" onClick={(event) => requestModeSwitch(event, 'pipeline')}>Pipeline</a>
          </nav>
        </div>
        <div className="quote-pipeline-topbar__stats" aria-label="Pipeline status">
          <MetricCard label="Products" value={String(liveOffers.length > 0 ? liveFilteredOffers.length : filteredProducts.length)} detail={liveOffers.length > 0 ? `${liveOffers.length} LoanHouse offers` : `${tenantHomePreviewProducts.length} total`} />
          {activeFilterCount > 0 ? <MetricCard label="Filters" value={String(activeFilterCount)} detail="active" /> : null}
          {completionPercent > 0 ? <MetricCard label="Profile" value={`${completionPercent}%`} detail="complete" tone={quoteReady ? 'ready' : startReady ? 'attention' : undefined} /> : null}
        </div>
        <div className="quote-pipeline-topbar__actions" aria-label="Pipeline actions">
          <button type="button" onClick={openSavePipelineModal}>Save Pipeline</button>
          <button type="button" onClick={() => { setRetrieveKeys(identifierKeysFromValues(values)); setRetrieveError(''); setRetrieveOpen(true); window.setTimeout(() => focusModalIdentifierField('retrieve', 'borrowerLastName'), 0); }}>Retrieve Pipeline</button>
        </div>
      </div>

      <ResumeDraft draftId={draftId} backup={resumeDismissed ? null : resumeBackup} loading={resumeLoading} error={resumeError} onResume={resumeDraft} onDismiss={() => setResumeDismissed(true)} />
      <LaunchBanner state={flowState} onRetry={() => { setFlowState({ kind: 'idle' }); onRetry?.(); }} />
      {showProgressiveCompatibility ? <ProgressiveCompatibilityNav errors={visibleErrors} /> : null}
      {showProgressiveCompatibility ? (
        <div className="quote-intake-fields quote-intake-fields--compatibility" aria-label="Pipeline identity fields">
          <InlineTextField label="Quote intent" name="quoteIntent" value={values.quoteIntent ?? ''} error={visibleErrors.quoteIntent} onChange={(value) => changeField('quoteIntent', value)} />
          {dropdownConfigState.kind === 'ready' && (fieldDropdownOptions.channel ?? []).length > 0 ? (
            <InlineSelectField label="Channel" name="channel" value={values.channel ?? ''} error={visibleErrors.channel} options={fieldDropdownOptions.channel ?? []} loading={false} onChange={(value) => changeField('channel', value)} />
          ) : (
            <InlineTextField label="Channel" name="channel" value={values.channel ?? ''} error={visibleErrors.channel} onChange={(value) => changeField('channel', value)} />
          )}
          <p className="quote-intake-status">Capture the borrower and loan facts needed to start a pricing run; pricing rules remain service-owned.</p>
        </div>
      ) : null}
      {dropdownConfigState.kind === 'loading' ? <p className="quote-intake-status" role="status">Loading tenant dropdown options...</p> : null}
      {dropdownConfigState.kind === 'fallback' ? <p className="quote-intake-status" aria-live="polite">{dropdownConfigState.message}</p> : null}
      {applicationFormRuntimeState.kind === 'loading' ? <p className="quote-intake-status" role="status">Loading application fields...</p> : null}
      {applicationFormRuntimeState.kind === 'loaded' ? <p className="quote-intake-status" aria-live="polite">Application fields loaded.</p> : null}
      {applicationFormRuntimeState.kind === 'missing' || applicationFormRuntimeState.kind === 'unreachable' ? <p className="quote-intake-status" role="alert">{applicationFieldsUnavailableText(applicationFormRuntimeState.message)}</p> : null}
      {statusMessage ? <p className="quote-intake-status" role="status">{statusMessage}</p> : null}
      {validationBannerVisible ? <ValidationTopBanner fields={actionableMissingRequiredFields} onClose={() => setValidationBannerDismissed(true)} onFocusField={focusPipelineField} /> : null}
      <DataRequiredPanel fields={actionableMissingRequiredFields} onNextField={focusNextRequiredField} />

      <form id="pipeline-product-form" className="quote-intake-form quote-intake-form--product-finder quote-intake-form--single-page" onSubmit={(event) => void launch(event)} onBlur={handleBlur} noValidate>
        <aside className="quote-product-filter-panel" aria-label="Pipeline filters">
          {applicationFormRuntimeState.kind === 'loaded' ? <ApplicationFormRuntimeRenderer sections={runtimeSections} values={values} errors={visibleErrors} dropdownOptions={fieldDropdownOptions} dropdownLoading={dropdownConfigState.kind === 'loading'} onChange={changeField} /> : null}

          {startFields().length > 0 ? (
            <FilterCard title="Keys" eyebrow="Save" badge={startReady ? 'Ready' : 'Required fields'}>
              <StepFields fields={startFields()} intake={values} errors={visibleErrors} onChange={changeField} dropdownOptions={fieldDropdownOptions} dropdownLoading={dropdownConfigState.kind === 'loading'} />
            </FilterCard>
          ) : null}

          <FilterCard title="Filters" eyebrow="Products" badge={activeFilterCount > 0 ? `${activeFilterCount} active` : undefined}>
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
              <span className="quote-product-count-summary" aria-live="polite">{productGridCountSummary}</span>
              <span className="quote-live-indicator"><span aria-hidden="true" />Live</span>
            </div>
          </div>

          {(liveOfferState.kind === 'loaded' ? liveFilteredOffers.length === 0 : filteredProducts.length === 0) ? (
            <div className="quote-product-empty" role="status">
              <strong>No matches.</strong>
            </div>
          ) : null}

          {liveOfferState.kind === 'loaded' ? (
            <LiveOfferGrid runId={liveOfferState.runId} offers={liveFilteredOffers} selectedOfferId={selectedLiveOfferId} onSelect={selectLiveOffer} onDetail={navigateLiveOfferDetail} onLock={navigateLiveOfferLock} />
          ) : (
            <div className="quote-product-grid quote-product-grid--rows" role="table" aria-label="Filtered mortgage products" style={productGridStyle}>
              <div className="quote-product-row quote-product-row--header" role="row">
                {priceScenarioColumns.map((column) => <div key={column.key} role="columnheader">{column.label}</div>)}
              </div>
              {filteredProducts.map((product) => (
                <ProductResultRow key={product.productCode} product={product} values={values} columns={priceScenarioColumns} missingFields={missingRequiredFields} metadata={runtimeMetadata} selected={selectedProduct?.productCode === product.productCode} actionState={rowActionState?.productCode === product.productCode ? rowActionState : null} onSelect={selectProduct} onApply={applyProduct} onAction={requestRowAction} />
              ))}
            </div>
          )}
          {liveOfferState.kind === 'loading' ? <p className="quote-intake-status" role="status">Loading LoanHouse-backed BFF offers...</p> : null}
          {liveOfferState.kind === 'unavailable' ? <p className="quote-intake-status" role="alert">{liveOfferState.message}</p> : null}
        </section>

        <div className="quote-pipeline-launchbar">
          <div>
            <strong>{selectedProduct?.productName ?? 'Product selection pending'}</strong>
            <span id="quote-launch-state">{launchStatus}</span>
          </div>
          <button type="submit" className="quote-intake-primary" disabled={launchDisabled} aria-describedby="quote-launch-state">{flowState.kind === 'submitting' ? 'Launching...' : 'Launch Quote'}</button>
        </div>
      </form>

      {selectedProduct && productDetailOpen ? <ProductDetailPanel product={selectedProduct} values={values} metadata={runtimeMetadata} onBack={() => setProductDetailOpen(false)} onUse={applyProduct} /> : null}
      {renderModeSwitchDialog()}
      {saveOpen ? (
        <div className="quote-pipeline-modal" role="dialog" aria-modal="true" aria-labelledby="save-pipeline-heading">
          <div className="quote-pipeline-modal__card">
            <div className="quote-pipeline-modal__header">
              <h3 id="save-pipeline-heading">Save Pipeline</h3>
              <button type="button" onClick={() => setSaveOpen(false)} aria-label="Close">×</button>
            </div>
            <p className="quote-pipeline-modal__copy">Add only the missing identifiers needed to save this pipeline.</p>
            {saveMissingFields.includes('borrowerLastName') ? <CompactField id="pipeline-save-borrowerLastName" label="Borrower Last Name" value={saveKeys.borrowerLastName} onChange={(value) => setSaveKeys((current) => ({ ...current, borrowerLastName: value }))} required /> : null}
            {saveMissingFields.includes('loanNumber') ? <CompactField id="pipeline-save-loanNumber" label="Loan Number" value={saveKeys.loanNumber} onChange={(value) => setSaveKeys((current) => ({ ...current, loanNumber: value }))} required /> : null}
            {saveError ? <p className="quote-intake-error" role="alert">{saveError}</p> : null}
            <div className="quote-pipeline-modal__actions">
              <button type="button" className="quote-filter-clear" onClick={() => setSaveOpen(false)}>Back</button>
              <button type="button" className="quote-intake-primary" onClick={submitSaveIdentifiers}>Save</button>
            </div>
          </div>
        </div>
      ) : null}
      {retrieveOpen ? (
        <div className="quote-pipeline-modal" role="dialog" aria-modal="true" aria-labelledby="retrieve-pipeline-heading">
          <div className="quote-pipeline-modal__card">
            <div className="quote-pipeline-modal__header">
              <h3 id="retrieve-pipeline-heading">Retrieve Pipeline</h3>
              <button type="button" onClick={() => setRetrieveOpen(false)} aria-label="Close">×</button>
            </div>
            <p className="quote-pipeline-modal__copy">Enter identifiers here to retrieve a saved pipeline without adding required keys back to the intake form.</p>
            <CompactField id="pipeline-retrieve-borrowerLastName" label="Borrower Last Name" value={retrieveKeys.borrowerLastName} onChange={(value) => setRetrieveKeys((current) => ({ ...current, borrowerLastName: value }))} required />
            <CompactField id="pipeline-retrieve-loanNumber" label="Loan Number" value={retrieveKeys.loanNumber} onChange={(value) => setRetrieveKeys((current) => ({ ...current, loanNumber: value }))} required />
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

function routeForMode(mode: QuoteMode, product?: AuthorizedProduct | null) {
  if (mode === 'quickquote') return '/quote/start';
  return product?.productCode ? `/pipeline?product=${encodeURIComponent(product.productCode)}` : '/pipeline';
}

function LiveOfferGrid({ runId, offers, selectedOfferId, onSelect, onDetail, onLock }: { runId: string; offers: OfferSummary[]; selectedOfferId: string; onSelect: (runId: string, offer: OfferSummary) => void; onDetail: (runId: string, offer: OfferSummary) => void; onLock: (runId: string, offer: OfferSummary) => void }) {
  if (offers.length === 0) {
    return (
      <div className="quote-product-empty" role="status">
        <strong>No LoanHouse-backed offers match the active filters.</strong>
      </div>
    );
  }

  return (
    <div className="quote-product-grid quote-product-grid--rows" role="table" aria-label="LoanHouse-backed BFF offers">
      <div className="quote-product-row quote-product-row--header" role="row">
        <div role="columnheader">Product</div>
        <div role="columnheader">Investor</div>
        <div role="columnheader">Approval status</div>
        <div role="columnheader">Rate</div>
        <div role="columnheader">Price</div>
        <div role="columnheader">Payment</div>
        <div role="columnheader">Lock</div>
        <div role="columnheader">Source</div>
        <div role="columnheader">Actions</div>
      </div>
      {offers.slice(0, 100).map((offer) => {
        const selected = selectedOfferId === offer.offerId;
        return (
          <div key={offer.offerId} className="quote-product-row" role="row" aria-label={`${offer.productLabel ?? offer.offerId} ${offerStatusText(offer)}`} aria-selected={selected} data-selected={selected} data-product-status={offerStatusText(offer).toLowerCase()}>
            <div className="quote-product-cell quote-product-cell--product" role="cell" data-column="Product"><div className="quote-product-cell__product"><strong>{offer.productLabel ?? offer.offerId}</strong><span>{offer.offerId}</span><small>LoanHouse/LoanPass product · rank #{offer.rank}</small></div></div>
            <div className="quote-product-cell" role="cell" data-column="Investor">{valueOrNa(offer.investor)}</div>
            <div className="quote-product-cell" role="cell" data-column="Approval status"><span className={`quote-status-pill quote-status-pill--${offerStatusClass(offer)}`}>{offerStatusText(offer)}</span></div>
            <div className="quote-product-cell" role="cell" data-column="Rate">{valueOrNa(offer.rate)}</div>
            <div className="quote-product-cell" role="cell" data-column="Price">{valueOrNa(offerField(offer, ['price', 'finalPrice', 'priceBps', 'finalPriceBps', 'roundedFinalPrice']))}</div>
            <div className="quote-product-cell" role="cell" data-column="Payment">{valueOrNa(offer.payment)}</div>
            <div className="quote-product-cell" role="cell" data-column="Lock">{lockTextForOffer(offer)}</div>
            <div className="quote-product-cell" role="cell" data-column="Source"><SourceRefs refs={sourceRefsForOffer(offer)} /></div>
            <div className="quote-product-cell quote-product-cell--actions" role="cell" data-column="Actions">
              <div className="quote-product-row-actions" aria-label={`${offer.productLabel ?? offer.offerId} live offer actions`}>
                <button type="button" aria-label={`Select offer ${offer.offerId}`} aria-pressed={selected} onClick={() => onSelect(runId, offer)}>Select offer</button>
                <button type="button" aria-label={`Continue to offer detail ${offer.offerId}`} onClick={() => onDetail(runId, offer)}>Continue</button>
                <button type="button" aria-label={`Start lock request for offer ${offer.offerId}`} onClick={() => onLock(runId, offer)}>Lock</button>
              </div>
            </div>
          </div>
        );
      })}
      {offers.length > 100 ? <p role="status">Showing first 100 offers from {offers.length} LoanHouse-backed BFF offers.</p> : null}
    </div>
  );
}

function SourceRefs({ refs }: { refs: string[] }) {
  return <ul className="quickquote-comparison__traces">{refs.map((ref) => <li key={ref}><code>{ref}</code></li>)}</ul>;
}

function FilterCard({ title, eyebrow, badge, children }: { title: string; eyebrow: string; badge?: string; children: ReactNode }) {
  return (
    <section className="quote-filter-card" aria-labelledby={`${slug(title)}-heading`}>
      <div className="quote-filter-card__heading">
        <div><p className="eyebrow">{eyebrow}</p><h3 id={`${slug(title)}-heading`}>{title}</h3></div>
        {badge ? <span>{badge}</span> : null}
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
      <FilterCard title="Borrower Details" eyebrow="Profile" badge="Blocked">
        <p className="quote-intake-empty" role="status">Application fields are unavailable for this borrower profile.</p>
      </FilterCard>
    );
  }
  return (
    <FilterCard title="Borrower Details" eyebrow="Profile" badge="Ready">
      <div className="quote-runtime-form" aria-label="Borrower details form">
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

function ValidationTopBanner({ fields, onClose, onFocusField }: { fields: PipelineRequiredField[]; onClose: () => void; onFocusField: (fieldId: keyof BorrowerIntake) => void }) {
  if (fields.length === 0) return null;
  const firstField = fields[0];
  const remainingCount = fields.length - 1;
  return (
    <section className="quote-validation-top-banner" role="alert" aria-labelledby="quote-validation-top-banner-heading" data-first-invalid-field={String(firstField.fieldId)}>
      <div>
        <p className="eyebrow">Needs attention</p>
        <h3 id="quote-validation-top-banner-heading">{firstField.label} needs attention.</h3>
        {remainingCount > 0 ? <p>{remainingCount} more fields need attention.</p> : null}
      </div>
      <div className="quote-validation-top-banner__actions">
        <button type="button" className="quote-filter-clear" onClick={() => onFocusField(firstField.fieldId)}>Review field</button>
        <button type="button" className="quote-validation-top-banner__close" onClick={onClose} aria-label="Close validation banner">×</button>
      </div>
    </section>
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
        <span className="quote-intake-data-required__state" aria-label="Required field count">{fields.length} {fields.length === 1 ? 'field' : 'fields'} missing</span>
      </div>
      <ul aria-label="Missing required fields">
        {fields.map((field, index) => <li key={`${String(field.fieldId)}-${index}`} data-field-id={String(field.fieldId)}>{field.label}</li>)}
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
      <div className="quote-form-builder__states" aria-label="Application form builder states">
        <span>Headers: section only</span>
        <span>Order: tenant draft</span>
        <span>Visibility: rules</span>
      </div>
      {validationMessage ? <p className="quote-intake-error" role="alert">{validationMessage}</p> : null}
      <fieldset className="quote-form-builder__field-editor" aria-label="Custom field editor data types">
        <legend>Field editor</legend>
        <div className="quote-form-builder__states" aria-label="Field editor states">
          <span>Data type</span>
          <span>No formulas</span>
          <span>Approved enums</span>
        </div>
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
                  <li key={`${step.section}-${fieldId}-${index}`} data-field-id={fieldId} data-value-type={header ? 'header' : 'input'}>
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
    <label className={`quote-intake-field${error ? ' quote-intake-field--error' : ''}`}>
      {label}
      <select name={name} value={value} aria-invalid={Boolean(error)} onChange={(event) => onChange(event.target.value)} disabled={loading}>
        {renderedOptions.map((option) => <option key={option.value || 'empty'} value={option.value}>{option.label}</option>)}
      </select>
      {error ? <span className="quote-intake-error" role="alert">{error}</span> : null}
    </label>
  );
}

function InlineTextField({ label, name, value, error, onChange }: { label: string; name: keyof BorrowerIntake; value: string; error?: string; onChange: (value: string) => void }) {
  return (
    <label className={`quote-intake-field${error ? ' quote-intake-field--error' : ''}`}>
      {label}
      <input name={name} value={value} aria-invalid={Boolean(error)} onChange={(event) => onChange(event.target.value)} type="text" />
      {error ? <span className="quote-intake-error" role="alert">{error}</span> : null}
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

function CompactField({ id, label, value, onChange, placeholder, required, inputMode }: { id?: string; label: string; value: string; onChange: (value: string) => void; placeholder?: string; required?: boolean; inputMode?: 'numeric' | 'decimal' }) {
  return (
    <label className="quote-compact-filter-row" htmlFor={id}>
      <span>{label}{required ? <em aria-hidden="true"> *</em> : null}</span>
      <input id={id} value={value} placeholder={placeholder} inputMode={inputMode} onChange={(event) => onChange(event.target.value)} required={required} />
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

function ProductResultRow({ product, values, columns, missingFields, metadata, selected, comparisonSelected = false, actionState, onSelect, onApply, onAction, onCompare }: { product: AuthorizedProduct; values: BorrowerIntake; columns: PriceScenarioColumn[]; missingFields: PipelineRequiredField[]; metadata: ScenarioIntakeMetadata | null; selected: boolean; comparisonSelected?: boolean; actionState: ProductRowActionState | null; onSelect: (product: AuthorizedProduct) => void; onApply: (product: AuthorizedProduct) => void; onAction: (product: AuthorizedProduct, action: ProductRowAction) => void; onCompare?: (product: AuthorizedProduct) => void }) {
  return (
    <div className="quote-product-row" role="row" aria-label={`${product.productName} ${product.status}`} aria-selected={selected} data-selected={selected} data-comparison-selected={comparisonSelected} data-product-status={product.status.toLowerCase()} onClick={() => onSelect(product)} tabIndex={0} onKeyDown={(event) => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); onSelect(product); } }}>
      {columns.map((column) => <div key={column.key} className={`quote-product-cell quote-product-cell--${column.key}`} role="cell" data-column={column.label}>{renderProductColumn(column.key, product, values, missingFields, metadata, onApply, onAction, actionState, column, comparisonSelected, onCompare)}</div>)}
    </div>
  );
}

function renderProductColumn(key: PriceScenarioColumnKey, product: AuthorizedProduct, values: BorrowerIntake, missingFields: PipelineRequiredField[], metadata: ScenarioIntakeMetadata | null, onApply: (product: AuthorizedProduct) => void, onAction: (product: AuthorizedProduct, action: ProductRowAction) => void, actionState: ProductRowActionState | null, column?: PriceScenarioColumn, comparisonSelected = false, onCompare?: (product: AuthorizedProduct) => void) {
  if (key === 'product') return <div className="quote-product-cell__product"><strong>{product.productName}</strong><span>{product.productCode}</span><small>{productTypeLabel(product.productType)} · {channelLabel(product.channelCode)} · {product.investorCode}</small><small>{termFromProductName(product.productName)} lock/pricing context · {productAvailabilityLabel(product)}</small></div>;
  if (key === 'investor') return product.investorCode;
  if (key === 'status') return <span className={`quote-status-pill quote-status-pill--${statusClass(product.status)}`}>{product.status}</span>;
  if (key === 'adjustedRate') return <QuoteEconomicsMetric label={column?.label ?? 'Rate'} value={rateOutputValue(product, values)} missingLabel={column?.missingLabel ?? 'Rate output pending'} missingFields={missingFields} traceRef={metricTraceRef(product, 'rate', metadata)} />;
  if (key === 'lockPeriod') return values.desiredRateLockPeriod || values.requestedLockPeriodDays || termFromProductName(product.productName);
  if (key === 'adjustedPrice') return <QuoteEconomicsMetric label={column?.label ?? 'Price'} value={firstConfiguredOutput(values, ['calc@adjusted-price', 'adjustedPrice', 'secondaryAdjustment'])} missingLabel={column?.missingLabel ?? 'Price output pending'} missingFields={missingFields} traceRef={metricTraceRef(product, 'price', metadata)} />;
  if (key === 'payment') return <QuoteEconomicsMetric label={column?.label ?? 'Payment'} value={firstConfiguredOutput(values, ['calc@payment', 'monthlyPayment', 'totalLiabilityMonthlyPayment'])} missingLabel={column?.missingLabel ?? 'Payment output pending'} missingFields={missingFields} traceRef={metricTraceRef(product, 'payment', metadata)} />;
  if (key === 'pitiaItia') return values.additionalMonthlyHousingExpenses || values.additionalAnnualHousingExpenses || '—';
  if (key === 'dscr') return <QuoteEconomicsMetric label={column?.label ?? 'DSCR'} value={firstConfiguredOutput(values, ['calc@dscr', 'estimatedDSCR'])} missingLabel={column?.missingLabel ?? 'DSCR output pending'} missingFields={missingFields} traceRef={metricTraceRef(product, 'dscr', metadata)} />;
  if (key === 'fees') return <QuoteEconomicsMetric label={column?.label ?? 'Fees'} value={firstConfiguredOutput(values, ['calc@fees', 'calc@fee-summary', 'feeSummary', 'feesSummary', 'pricingFeeSummary'])} missingLabel={column?.missingLabel ?? 'Fee output pending'} missingFields={missingFields} traceRef={metricTraceRef(product, 'fees', metadata)} />;
  if (key === 'llpa') return <QuoteEconomicsMetric label={column?.label ?? 'LLPA'} value={firstConfiguredOutput(values, ['calc@llpa', 'calc@llpa-summary', 'llpaSummary', 'pricingLlpaSummary'])} missingLabel={column?.missingLabel ?? 'LLPA output pending'} missingFields={missingFields} traceRef={metricTraceRef(product, 'llpa', metadata)} />;
  if (key === 'audit') return (
    <div className="quote-product-audit-cell">
      <span>{productEligibilityReviewLabel(product)}</span>
      <button type="button" onClick={(event) => { event.stopPropagation(); onAction(product, 'status'); }}>Review status</button>
    </div>
  );
  if (key === 'ltv') return values.loanToValue || '—';
  if (key === 'fico') return values.decisionCreditScore || '—';
  if (column?.fieldId || isCalculationFieldId(key)) return renderCalculationOutput(column ?? { key, label: friendlyLabel(key), fieldId: key }, values);
  return (
    <div className="quote-product-row-actions" aria-label={`${product.productName} row actions`}>
      {onCompare ? <button type="button" onClick={(event) => { event.stopPropagation(); onCompare(product); }}>{comparisonSelected ? 'Remove from comparison' : 'Add to comparison'}</button> : null}
      <button type="button" onClick={(event) => { event.stopPropagation(); onApply(product); }} disabled={!isProductEligible(product)}>Use for quote</button>
      <button type="button" onClick={(event) => { event.stopPropagation(); onAction(product, 'refresh'); }} disabled={!isProductEligible(product)}>Queue pricing refresh</button>
      <button type="button" onClick={(event) => { event.stopPropagation(); onAction(product, 'lock'); }} disabled={!isProductEligible(product)}>Request lock</button>
      {!onCompare ? <button type="button" onClick={(event) => { event.stopPropagation(); onAction(product, 'status'); }}>Review status</button> : null}
      {actionState ? (
        <div className={`quote-product-row-action-status quote-product-row-action-status--${actionState.tone}`} role="status" onClick={(event) => event.stopPropagation()}>
          <strong>{actionState.message}</strong>
          {actionState.details.length > 0 ? <ul>{actionState.details.map((detail) => <li key={detail}>{detail}</li>)}</ul> : null}
        </div>
      ) : null}
    </div>
  );
}

function QuickQuoteComparisonPanel({ products, selectedProductCode, values, missingFields, metadata, onSelectProduct, onUseProduct }: { products: AuthorizedProduct[]; selectedProductCode: string; values: BorrowerIntake; missingFields: PipelineRequiredField[]; metadata: ScenarioIntakeMetadata | null; onSelectProduct: (product: AuthorizedProduct) => void; onUseProduct: (product: AuthorizedProduct) => void }) {
  const metricRows: Array<{ key: string; label: string }> = [
    { key: 'product', label: 'Product' },
    { key: 'eligibility', label: 'Eligibility' },
    { key: 'rate', label: 'Rate' },
    { key: 'price', label: 'Price' },
    { key: 'payment', label: 'Payment' },
    { key: 'dscr', label: 'DSCR' },
    { key: 'fees', label: 'Fees' },
    { key: 'llpa', label: 'LLPA' },
    { key: 'assumptions', label: 'Assumptions' },
    { key: 'trace', label: 'Trace availability' },
    { key: 'actions', label: 'Actions' },
  ];
  const comparisonGridStyle = { '--quickquote-comparison-columns': `minmax(9rem, 12rem) repeat(${Math.max(products.length, 1)}, minmax(13rem, 1fr))` } as CSSProperties;

  return (
    <section className="quickquote-comparison" aria-labelledby="quickquote-comparison-heading">
      <div className="quickquote-comparison__header">
        <div>
          <span className="eyebrow">Comparison</span>
          <h3 id="quickquote-comparison-heading">Responsive quote comparison</h3>
          <p>Selected products stay aligned by metric; missing, not-applicable, assumption, and trace states remain visible.</p>
        </div>
      </div>
      {products.length < 2 ? <p className="quickquote-comparison__notice" role="status">Select at least two products to compare side by side.</p> : null}
      <div className="quickquote-comparison__viewport" aria-label="Swipe QuickQuote comparison products">
        <div className="quickquote-comparison__grid" role="table" aria-label="QuickQuote product comparison" style={comparisonGridStyle}>
          <div className="quickquote-comparison__row quickquote-comparison__row--header" role="row">
            <div className="quickquote-comparison__metric-label" role="columnheader">Metric</div>
            {products.map((product) => <div key={product.productCode} role="columnheader" data-selected={selectedProductCode === product.productCode}>{product.productName}</div>)}
          </div>
          {metricRows.map((metric) => (
            <div key={metric.key} className="quickquote-comparison__row" role="row" aria-label={`${metric.label} comparison`}>
              <div className="quickquote-comparison__metric-label" role="rowheader">{metric.label}</div>
              {products.map((product) => <div key={`${product.productCode}-${metric.key}`} className="quickquote-comparison__cell" role="cell">{renderComparisonCell(metric.key, product, values, missingFields, metadata, onSelectProduct, onUseProduct)}</div>)}
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

function renderComparisonCell(metric: string, product: AuthorizedProduct, values: BorrowerIntake, missingFields: PipelineRequiredField[], metadata: ScenarioIntakeMetadata | null, onSelectProduct: (product: AuthorizedProduct) => void, onUseProduct: (product: AuthorizedProduct) => void) {
  if (metric === 'product') return <div className="quote-product-cell__product"><strong>{product.productName}</strong><span>{product.productCode}</span></div>;
  if (metric === 'eligibility') return <div className="quickquote-comparison__state"><span className={`quote-status-pill quote-status-pill--${statusClass(product.status)}`}>{product.status}</span><small>{productEligibilityReviewLabel(product)} · {productUnavailableReason(product)}</small></div>;
  if (metric === 'rate') return <QuoteEconomicsMetric label="Rate" value={rateOutputValue(product, values)} missingLabel="Rate output pending" missingFields={missingFields} traceRef={metricTraceRef(product, 'rate', metadata)} />;
  if (metric === 'price') return <QuoteEconomicsMetric label="Price" value={firstConfiguredOutput(values, ['calc@adjusted-price', 'adjustedPrice', 'secondaryAdjustment'])} missingLabel="Price output pending" missingFields={missingFields} traceRef={metricTraceRef(product, 'price', metadata)} />;
  if (metric === 'payment') return <QuoteEconomicsMetric label="Payment" value={firstConfiguredOutput(values, ['calc@payment', 'monthlyPayment', 'totalLiabilityMonthlyPayment'])} missingLabel="Payment output pending" missingFields={missingFields} traceRef={metricTraceRef(product, 'payment', metadata)} />;
  if (metric === 'dscr') return <QuoteEconomicsMetric label="DSCR" value={firstConfiguredOutput(values, ['calc@dscr', 'estimatedDSCR'])} missingLabel="DSCR output pending" missingFields={missingFields} traceRef={metricTraceRef(product, 'dscr', metadata)} />;
  if (metric === 'fees') return <QuoteEconomicsMetric label="Fees" value={firstConfiguredOutput(values, ['calc@fees', 'calc@fee-summary', 'feeSummary', 'feesSummary', 'pricingFeeSummary'])} missingLabel="Fee output pending" missingFields={missingFields} traceRef={metricTraceRef(product, 'fees', metadata)} />;
  if (metric === 'llpa') return <QuoteEconomicsMetric label="LLPA" value={firstConfiguredOutput(values, ['calc@llpa', 'calc@llpa-summary', 'llpaSummary', 'pricingLlpaSummary'])} missingLabel="LLPA output pending" missingFields={missingFields} traceRef={metricTraceRef(product, 'llpa', metadata)} />;
  if (metric === 'assumptions') return <QuickQuoteComparisonAssumptions product={product} fields={missingFields} metadata={metadata} />;
  if (metric === 'trace') return <QuickQuoteComparisonTrace product={product} metadata={metadata} />;
  return <div className="quickquote-comparison__actions"><button type="button" onClick={() => onSelectProduct(product)}>Select for quote</button><button type="button" onClick={() => onUseProduct(product)} disabled={!isProductEligible(product)}>Use for quote</button></div>;
}

function QuickQuoteComparisonAssumptions({ product, fields, metadata }: { product: AuthorizedProduct; fields: PipelineRequiredField[]; metadata: ScenarioIntakeMetadata | null }) {
  const assumptions = [
    ...fields.slice(0, 3).map((field) => `${field.label} missing`),
    ...(metadata?.validationIssues?.slice(0, 2).map((issue) => `${issue.code}: ${issue.message}`) ?? []),
    ...(metadata?.dependencyStatus && metadata.dependencyStatus !== 'READY' ? [`Dependency status: ${metadata.dependencyStatus}`] : []),
    ...(isProductEligible(product) ? [] : [productUnavailableReason(product)]),
  ];
  if (assumptions.length === 0) return <span className="quickquote-comparison__empty-state">No visible assumptions from current intake state.</span>;
  return <ul className="quickquote-comparison__assumptions">{assumptions.map((assumption) => <li key={assumption}>{assumption}</li>)}</ul>;
}

function QuickQuoteComparisonTrace({ product, metadata }: { product: AuthorizedProduct; metadata: ScenarioIntakeMetadata | null }) {
  const refs = productAuditTraceRefs(product, metadata);
  if (refs.length === 0) return <span className="quickquote-comparison__empty-state">Trace unavailable</span>;
  return <ul className="quickquote-comparison__traces">{refs.slice(0, 4).map((ref) => <li key={`${ref.label}-${ref.value}`}><a href={traceHref(ref)}>{ref.label}: {ref.value}</a></li>)}</ul>;
}

function QuoteEconomicsMetric({ label, value, missingLabel, missingFields, traceRef }: { label: string; value: string; missingLabel: string; missingFields: PipelineRequiredField[]; traceRef: ProductMetricTraceRef | null }) {
  const metricValue = value.trim();
  const state = notApplicableValue(metricValue) ? 'not-applicable' : metricValue ? 'ready' : 'missing';
  const displayValue = state === 'ready' ? metricValue : state === 'not-applicable' ? 'N/A' : missingFields.length > 0 ? 'Missing data' : missingLabel;
  const detail = state === 'ready' ? 'Configured output' : state === 'not-applicable' ? 'Not applicable' : missingFactsSummary(missingFields, missingLabel);
  return (
    <div className="quote-economics-metric" data-metric-state={state} aria-label={`${label}: ${displayValue}`}>
      <strong>{displayValue}</strong>
      <small>{detail}</small>
      {traceRef ? <a href={traceHref(traceRef)} onClick={(event) => event.stopPropagation()} onKeyDown={(event) => event.stopPropagation()}>{traceRef.label} trace</a> : null}
    </div>
  );
}

function notApplicableValue(value: string) {
  return ['n/a', 'na', 'not applicable', 'not-applicable'].includes(value.trim().toLowerCase());
}

function missingFactsSummary(fields: PipelineRequiredField[], fallback: string) {
  if (fields.length === 0) return fallback;
  const labels = fields.slice(0, 2).map((field) => field.label).join(', ');
  return `Needs ${labels}${fields.length > 2 ? ` +${fields.length - 2}` : ''}`;
}

function firstConfiguredOutput(values: BorrowerIntake, fieldIds: string[]) {
  const outputValues = values as Record<string, string | undefined>;
  for (const fieldId of fieldIds) {
    const value = outputValues[fieldId]?.trim();
    if (value) return value;
  }
  return '';
}

function rateOutputValue(product: AuthorizedProduct, values: BorrowerIntake) {
  return firstConfiguredOutput(values, ['calc@final-interest-rate', 'calc@rate']) || rateValueFromProduct(product);
}

function rateValueFromProduct(product: AuthorizedProduct) {
  if (product.baseRateMin != null && product.baseRateMax != null) return `${product.baseRateMin.toFixed(3)}% - ${product.baseRateMax.toFixed(3)}%`;
  if (product.baseRateMin != null || product.baseRateMax != null) return `${(product.baseRateMin ?? product.baseRateMax)?.toFixed(3)}%`;
  if (product.rateSpread != null) return `${product.rateSpread.toFixed(2)} spread`;
  return '';
}

function metricTraceRef(product: AuthorizedProduct, metric: QuoteEconomicsMetricKey, metadata: ScenarioIntakeMetadata | null) {
  const refs = productAuditTraceRefs(product, metadata);
  const normalizedMetric = normalized(metric);
  const labelByMetric: Record<QuoteEconomicsMetricKey, string> = {
    rate: 'Pricing',
    price: 'Pricing',
    payment: 'Calculation',
    dscr: 'Calculation',
    fees: 'Fee',
    llpa: 'LLPA',
  };
  return refs.find((ref) => ref.metric && normalized(ref.metric) === normalizedMetric)
    ?? refs.find((ref) => !ref.metric && ref.label === labelByMetric[metric])
    ?? null;
}

function traceAnchorId(value: string) {
  return `quote-trace-${value.replace(/[^a-zA-Z0-9_-]+/g, '-').replace(/^-+|-+$/g, '').toLowerCase() || 'pending'}`;
}

function traceHref(ref: ProductMetricTraceRef) {
  const target = (ref.href || ref.value).trim();
  if (target.startsWith('#') || target.startsWith('/')) return target;
  return `#${traceAnchorId(ref.value)}`;
}

function traceTargetId(ref: ProductMetricTraceRef) {
  const href = traceHref(ref);
  return href.startsWith('#') ? href.slice(1) : traceAnchorId(ref.value);
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

function ProductDetailPanel({ product, values, metadata, onBack, onUse }: { product: AuthorizedProduct; values: BorrowerIntake; metadata: ScenarioIntakeMetadata | null; onBack: () => void; onUse: (product: AuthorizedProduct) => void }) {
  const traceRefs = productAuditTraceRefs(product, metadata);
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
      <section className="quote-product-audit-details" aria-label="Product review details">
        <h4>Review details</h4>
        <p>{productUnavailableReason(product)}</p>
        <dl>
          {traceRefs.length > 0
            ? traceRefs.map((ref) => <div key={`${ref.label}:${ref.value}`} id={traceTargetId(ref)}><dt>{ref.label}</dt><dd>{ref.value}</dd></div>)
            : <div data-trace-state="unavailable"><dt>Trace evidence</dt><dd>Trace evidence unavailable from configured/API product data.</dd></div>}
        </dl>
      </section>
      <button type="button" className="quote-intake-primary" onClick={() => onUse(product)}>Use for quote</button>
    </aside>
  );
}

function quoteLaunchSelectedProduct(product: AuthorizedProduct): QuoteLaunchSelectedProduct {
  return {
    productCode: product.productCode,
    productName: product.productName,
    investorCode: product.investorCode,
    channelCode: product.channelCode,
    productType: product.productType,
  };
}

function MetricCard({ label, value, detail, tone }: { label: string; value: string; detail: string; tone?: 'ready' | 'attention' }) {
  return <div className="quote-metric-card" data-tone={tone}><span>{label}</span><strong>{value}</strong><small>{detail}</small></div>;
}

function QuickQuoteStateCard({ title, state, detail }: { title: string; state: 'loading' | 'ready' | 'empty'; detail: string }) {
  return <div className="quickquote-state-card" data-state={state} role="status" aria-label={`${title} ${state}`}><span>{title}</span><strong>{state === 'loading' ? 'Loading' : state === 'empty' ? 'Empty' : 'Ready'}</strong><small>{detail}</small></div>;
}

function QuickQuotePricingInputs({ fields, values, errors, dropdownOptions, dropdownLoading, selectedProduct, missingFields, onChange, onFocusField }: { fields: ScenarioIntakeField[]; values: BorrowerIntake; errors: IntakeFieldErrors; dropdownOptions: DropdownOptionsByField; dropdownLoading: boolean; selectedProduct: AuthorizedProduct | null; missingFields: PipelineRequiredField[]; onChange: (field: keyof BorrowerIntake, value: string, options?: { autoSave?: boolean }) => void; onFocusField: (fieldId: keyof BorrowerIntake) => void }) {
  const missingIds = new Set(missingFields.map((field) => String(field.fieldId)));
  const productChannel = selectedProduct?.channelCode ?? '';
  const firstMissingField = missingFields[0];
  return (
    <section className="quickquote-pricing-inputs" aria-labelledby="quickquote-pricing-inputs-heading">
      <div className="quickquote-pricing-inputs__header">
        <div>
          <span className="eyebrow">Pricing inputs</span>
          <h3 id="quickquote-pricing-inputs-heading">Channel and loan purpose</h3>
        </div>
        <span className="quickquote-pricing-inputs__state" data-ready={missingFields.length === 0}>{missingFields.length === 0 ? 'Ready' : `${missingFields.length} missing`}</span>
      </div>
      <StepFields fields={fields} intake={values} errors={errors} onChange={onChange} dropdownOptions={dropdownOptions} dropdownLoading={dropdownLoading} />
      <div className="quickquote-pricing-inputs__controls" aria-label="QuickQuote pricing fill controls">
        <button type="button" className="quote-filter-clear" onClick={() => productChannel && onChange('channel', productChannel, { autoSave: false })} disabled={!productChannel || !missingIds.has('channel')}>Use selected product channel</button>
        <button type="button" className="quote-filter-clear" onClick={() => firstMissingField && onFocusField(firstMissingField.fieldId)} disabled={!firstMissingField}>Review first missing input</button>
      </div>
    </section>
  );
}

function QuickQuotePrefillGroups({ sections, values, mappings, overrides }: { sections: Array<{ step: QuoteIntakeStepDefinition; fields: ScenarioIntakeField[] }>; values: BorrowerIntake; mappings: QuickQuoteLosPrefillMapping[]; overrides: Record<string, LosOverrideAudit> }) {
  const visible = sections.map(({ step, fields }) => ({ step, fields: fields.filter((field) => !isSaveRetrieveIdentifierField(String(field.fieldId))) })).filter(({ fields }) => fields.length > 0).slice(0, 6);
  if (visible.length === 0) return <div className="quickquote-prefill-empty" role="status">No mapped borrower detail groups.</div>;
  return (
    <ol className="quickquote-prefill-groups" aria-label="Mapped borrower detail groups">
      {visible.map(({ step, fields }) => {
        const mapped = fields.filter((field) => (values[field.fieldId] ?? '').trim()).length;
        return (
          <li key={step.section}>
            <div className="quickquote-prefill-group__summary"><span>{step.label}</span><strong>{mapped}/{fields.length}</strong></div>
            <ul className="quickquote-prefill-metadata" aria-label={`${step.label} borrower detail status`}>
              {fields.slice(0, 4).map((field, index) => {
                const mapping = mappingForField(mappings, field);
                const override = overrides[String(field.fieldId)];
                return (
                  <li key={`${String(field.fieldId)}-${index}`} data-value-state={override ? 'overridden' : (values[field.fieldId] ?? '').trim() ? 'supplied' : 'missing'}>
                    <span>{field.label}</span>
                    <small>{prefillValueState(field, values, override)} · {missingCategoryLabel(mapping.missingCategory)}</small>
                  </li>
                );
              })}
            </ul>
          </li>
        );
      })}
    </ol>
  );
}

function QuickQuoteMissingFacts({ fields, mappings, values }: { fields: PipelineRequiredField[]; mappings: QuickQuoteLosPrefillMapping[]; values: BorrowerIntake }) {
  const missingMapped = mappings.filter((mapping) => !(values[mapping.fieldId] ?? '').trim());
  const grouped = missingPrefillGroups(fields, missingMapped);
  return (
    <section className="quickquote-missing-facts" aria-labelledby="quickquote-missing-heading">
      <h3 id="quickquote-missing-heading">Missing facts</h3>
      <div className="quickquote-reason-chips" aria-label="Missing fact reason categories">
        <span>Required to price: {grouped.requiredToPrice.length}</span>
        <span>Improves pricing: {grouped.improvesPricing.length}</span>
        <span>Before lock: {grouped.requiredBeforeLock.length}</span>
      </div>
      {missingMapped.length > 0 ? <ul>{missingMapped.slice(0, 8).map((mapping, index) => <li key={`${String(mapping.fieldId)}-${index}`}>{mapping.wcpeField} <small>{missingCategoryLabel(mapping.missingCategory)}</small></li>)}</ul> : fields.length > 0 ? <ul>{fields.slice(0, 6).map((field, index) => <li key={`${String(field.fieldId)}-${index}`}>{field.label}</li>)}</ul> : <span className="quickquote-ready-chip">No required pricing facts missing</span>}
    </section>
  );
}

function QuickQuoteMappedFieldOverrides({ fields, values, errors, dropdownOptions, dropdownLoading, onChange }: { fields: ScenarioIntakeField[]; values: BorrowerIntake; errors: IntakeFieldErrors; dropdownOptions: DropdownOptionsByField; dropdownLoading: boolean; onChange: (field: keyof BorrowerIntake, value: string) => void }) {
  if (fields.length === 0) return null;
  return (
    <section className="quickquote-mapped-overrides" aria-labelledby="quickquote-mapped-overrides-heading">
      <h3 id="quickquote-mapped-overrides-heading">Borrower details</h3>
      <p>Edit borrower details before saving the draft or launching pricing.</p>
      <StepFields fields={fields} intake={values} errors={errors} onChange={onChange} dropdownOptions={dropdownOptions} dropdownLoading={dropdownLoading} />
    </section>
  );
}

function QuickQuoteOverrideAudit({ overrides }: { overrides: LosOverrideAudit[] }) {
  return (
    <section className="quickquote-override-audit" aria-labelledby="quickquote-override-heading">
      <h3 id="quickquote-override-heading">Recent edits</h3>
      {overrides.length === 0 ? <p>No imported values were edited in this draft.</p> : (
        <ul>
          {overrides.map((override) => (
            <li key={String(override.fieldId)}>
              <strong>{override.label}</strong>
              <span>Original value: {override.originalValue}</span>
              <span>Edited value: {override.overrideValue}</span>
              <span>Edited by: {override.actor} at {override.changedAt}</span>
              <small>Pricing impact is rechecked when the quote is launched.</small>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function quickQuotePrefillMappingsForMetadata(metadata: ScenarioIntakeMetadata | null, sections: Array<{ step: QuoteIntakeStepDefinition; fields: ScenarioIntakeField[] }>): QuickQuoteLosPrefillMapping[] {
  const configured = metadata?.quickQuoteState?.losPrefillMappings?.filter((mapping) => mapping.fieldId in initialQuoteIntake && !isSaveRetrieveIdentifierField(String(mapping.fieldId))) ?? [];
  if (configured.length > 0) return configured;
  const requiredFacts = new Set(metadata?.quickQuoteState?.quoteServiceRequiredFacts ?? []);
  return sections.flatMap(({ fields }) => fields.filter((field) => !isSaveRetrieveIdentifierField(String(field.fieldId))).map((field) => deriveLosPrefillMapping(field, requiredFacts)));
}

function deriveLosPrefillMapping(field: ScenarioIntakeField, requiredFacts: Set<string>): QuickQuoteLosPrefillMapping {
  const source = field.sourceRef || 'LOS adapter mapping pending';
  const [sourceSystem, sourceKey] = source.includes(':') ? source.split(/:(.+)/, 2) : ['LOS adapter', source];
  return {
    fieldId: field.fieldId,
    sourceSystem,
    losFieldLabel: field.label,
    losFieldKey: sourceKey || String(field.fieldId),
    wcpeField: String(field.fieldId),
    confidence: field.decisionQuality === 'VERIFIED' ? 'VERIFIED' : field.decisionQuality === 'CONFLICTING' ? 'CONFLICTING' : source.toLowerCase().includes('loanpass') ? 'MAPPED' : 'UNKNOWN',
    lastSync: 'pending configured LOS adapter',
    missingCategory: requiredFacts.has(String(field.fieldId)) || field.required ? 'required_to_price' : isLockRelatedField(field) ? 'required_before_lock' : 'improves_pricing',
    scope: 'tenant/channel scoped by metadata response',
    authorizationState: 'uses tenant quote-runs metadata endpoint',
    affectedOutputTraces: [`quote-fact:${String(field.fieldId)}`, `prefill-source:${source}`],
  };
}

function mappingForField(mappings: QuickQuoteLosPrefillMapping[], field: ScenarioIntakeField) {
  return mappings.find((mapping) => mapping.fieldId === field.fieldId) ?? deriveLosPrefillMapping(field, new Set());
}

function prefillValueState(field: ScenarioIntakeField, values: BorrowerIntake, override?: LosOverrideAudit) {
  if (override) return 'Overridden';
  return (values[field.fieldId] ?? '').trim() ? 'Supplied' : 'Missing';
}

function missingPrefillGroups(_fields: PipelineRequiredField[], mappings: QuickQuoteLosPrefillMapping[]) {
  return {
    requiredToPrice: mappings.filter((mapping) => mapping.missingCategory === 'required_to_price'),
    improvesPricing: mappings.filter((mapping) => mapping.missingCategory === 'improves_pricing'),
    requiredBeforeLock: mappings.filter((mapping) => mapping.missingCategory === 'required_before_lock'),
  };
}

function missingCategoryLabel(category: QuickQuoteLosPrefillMapping['missingCategory']) {
  if (category === 'required_to_price') return 'required to price';
  if (category === 'required_before_lock') return 'required before lock';
  return 'improves pricing';
}

function isLockRelatedField(field: ScenarioIntakeField) {
  return /lock/i.test(`${String(field.fieldId)} ${field.label} ${field.sourceRef}`);
}

function withLosOverrideContext(values: BorrowerIntake, overrides: LosOverrideAudit[]): BorrowerIntake {
  if (overrides.length === 0) return values;
  return {
    ...values,
    clientContext: JSON.stringify({
      ...(safeJsonRecord(values.clientContext)),
      losOverrides: overrides,
    }),
  };
}

function withQuickQuoteDraftContext(values: BorrowerIntake, options: {
  sourceMode: QuoteMode;
  selectedProduct: AuthorizedProduct | null;
  comparedProducts: AuthorizedProduct[];
  productFilters: ProductFilterState;
  missingFields: PipelineRequiredField[];
  metadata: ScenarioIntakeMetadata | null;
  losPrefillMappings: QuickQuoteLosPrefillMapping[];
}): BorrowerIntake {
  const quickQuoteDraft: QuickQuoteDraftContext = {
    storyId: 'PII-77-S07',
    sourceMode: options.sourceMode,
    selectedProduct: options.selectedProduct ? quoteLaunchSelectedProduct(options.selectedProduct) : undefined,
    comparedProducts: options.comparedProducts.map(quoteLaunchSelectedProduct),
    productFilters: options.productFilters,
    quoteOutputs: quoteOutputSnapshot(options.comparedProducts, values, options.missingFields, options.metadata),
    visibleAssumptions: quickQuoteDraftAssumptions(options.missingFields, options.metadata),
    traceReferences: selectedOrComparedTraceRefs(options.selectedProduct, options.comparedProducts, options.metadata),
    losPrefillMappings: options.losPrefillMappings,
    savedAt: new Date().toISOString(),
  };
  return {
    ...values,
    clientContext: JSON.stringify({
      ...(safeJsonRecord(values.clientContext)),
      quickQuoteDraft,
    }),
  };
}

function quickQuoteDraftContextFromClientContext(clientContext: string | undefined): QuickQuoteDraftContext | null {
  const candidate = safeJsonRecord(clientContext).quickQuoteDraft;
  if (!candidate || typeof candidate !== 'object' || Array.isArray(candidate)) return null;
  const record = candidate as Partial<QuickQuoteDraftContext>;
  if (record.storyId !== 'PII-77-S07') return null;
  return {
    storyId: 'PII-77-S07',
    sourceMode: record.sourceMode === 'pipeline' ? 'pipeline' : 'quickquote',
    selectedProduct: record.selectedProduct,
    comparedProducts: Array.isArray(record.comparedProducts) ? record.comparedProducts : [],
    productFilters: isProductFilterState(record.productFilters) ? record.productFilters : emptyProductFilters,
    quoteOutputs: Array.isArray(record.quoteOutputs) ? record.quoteOutputs : [],
    visibleAssumptions: Array.isArray(record.visibleAssumptions) ? record.visibleAssumptions.filter((value): value is string => typeof value === 'string') : [],
    traceReferences: Array.isArray(record.traceReferences) ? record.traceReferences.filter(isProductMetricTraceRef) : [],
    losPrefillMappings: Array.isArray(record.losPrefillMappings) ? record.losPrefillMappings.filter(isQuickQuoteLosPrefillMapping) : [],
    savedAt: typeof record.savedAt === 'string' ? record.savedAt : '',
  };
}

function quoteOutputSnapshot(products: AuthorizedProduct[], values: BorrowerIntake, missingFields: PipelineRequiredField[], metadata: ScenarioIntakeMetadata | null): QuickQuoteDraftContext['quoteOutputs'] {
  const metrics: QuoteEconomicsMetricKey[] = ['rate', 'price', 'payment', 'dscr', 'fees', 'llpa'];
  return products.flatMap((product) => metrics.map((metric) => {
    const value = quoteOutputMetricValue(product, values, metric);
    return {
      productCode: product.productCode,
      metric,
      value,
      state: value ? 'ready' as const : missingFields.length > 0 ? 'assumption' as const : 'missing' as const,
      traceRef: metricTraceRef(product, metric, metadata) ?? undefined,
    };
  }));
}

function quoteOutputMetricValue(product: AuthorizedProduct, values: BorrowerIntake, metric: QuoteEconomicsMetricKey) {
  if (metric === 'rate') return rateOutputValue(product, values);
  if (metric === 'price') return firstConfiguredOutput(values, ['calc@adjusted-price', 'adjustedPrice', 'secondaryAdjustment']);
  if (metric === 'payment') return firstConfiguredOutput(values, ['calc@payment', 'monthlyPayment', 'totalLiabilityMonthlyPayment']);
  if (metric === 'dscr') return firstConfiguredOutput(values, ['calc@dscr', 'estimatedDSCR']);
  if (metric === 'fees') return firstConfiguredOutput(values, ['calc@fees', 'calc@fee-summary', 'feeSummary', 'feesSummary', 'pricingFeeSummary']);
  return firstConfiguredOutput(values, ['calc@llpa', 'calc@llpa-summary', 'llpaSummary', 'pricingLlpaSummary']);
}

function quickQuoteDraftAssumptions(missingFields: PipelineRequiredField[], metadata: ScenarioIntakeMetadata | null) {
  const missing = missingFields.map((field) => `${field.label} (${String(field.fieldId)}) must be confirmed before lock or final submission.`);
  const blockers = metadata?.validationIssues?.map((issue) => `${issue.code}: ${issue.message}`) ?? [];
  const dependency = metadata?.dependencyStatus && metadata.dependencyStatus !== 'READY' ? [`Dependency status: ${metadata.dependencyStatus}`] : [];
  const assumptions = [...missing, ...blockers, ...dependency];
  return assumptions.length > 0 ? assumptions : ['QuickQuote outputs remain preliminary until configured pricing, eligibility, and lock evidence confirms them.'];
}

function selectedOrComparedTraceRefs(selectedProduct: AuthorizedProduct | null, comparedProducts: AuthorizedProduct[], metadata: ScenarioIntakeMetadata | null) {
  const products = selectedProduct ? [selectedProduct, ...comparedProducts.filter((product) => product.productCode !== selectedProduct.productCode)] : comparedProducts;
  const seen = new Set<string>();
  return products.flatMap((product) => productAuditTraceRefs(product, metadata)).filter((ref) => {
    const key = `${ref.label}:${ref.value}:${ref.href ?? ''}`;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

function isProductFilterState(value: unknown): value is ProductFilterState {
  const candidate = value as Partial<Record<keyof ProductFilterState, unknown>>;
  return Boolean(candidate) && Object.keys(emptyProductFilters).every((key) => typeof candidate[key as keyof ProductFilterState] === 'string');
}

function isProductMetricTraceRef(value: unknown): value is ProductMetricTraceRef {
  const candidate = value as Partial<ProductMetricTraceRef>;
  return typeof candidate?.label === 'string' && typeof candidate.value === 'string';
}

function isQuickQuoteLosPrefillMapping(value: unknown): value is QuickQuoteLosPrefillMapping {
  const candidate = value as Partial<QuickQuoteLosPrefillMapping>;
  return typeof candidate?.fieldId === 'string' && typeof candidate.sourceSystem === 'string' && typeof candidate.wcpeField === 'string';
}

function overrideAuditFromClientContext(clientContext: string | undefined): Record<string, LosOverrideAudit> {
  const parsed = safeJsonRecord(clientContext);
  const overrides = Array.isArray(parsed.losOverrides) ? parsed.losOverrides : [];
  return overrides.filter(isLosOverrideAudit).reduce((acc, override) => ({ ...acc, [String(override.fieldId)]: override }), {} as Record<string, LosOverrideAudit>);
}

function safeJsonRecord(value: string | undefined): Record<string, unknown> {
  if (typeof value !== 'string') return {};
  if (!value.trim()) return {};
  try {
    const parsed = JSON.parse(value) as unknown;
    return typeof parsed === 'object' && parsed !== null && !Array.isArray(parsed) ? parsed as Record<string, unknown> : {};
  } catch {
    return {};
  }
}

function isLosOverrideAudit(value: unknown): value is LosOverrideAudit {
  const candidate = value as Partial<LosOverrideAudit>;
  return typeof candidate?.fieldId === 'string' && typeof candidate.label === 'string' && typeof candidate.originalValue === 'string' && typeof candidate.overrideValue === 'string' && typeof candidate.changedAt === 'string' && Array.isArray(candidate.affectedOutputTraces);
}

function QuickQuoteSkeleton() {
  return (
    <div className="quickquote-skeleton" role="status" aria-label="QuickQuote loading state">
      <span>Data mapping</span>
      <span>Pricing readiness</span>
      <span>Product grid</span>
    </div>
  );
}

function QuickQuoteSubmitGate({ missingFacts }: { missingFacts: number }) {
  return (
    <section className="quickquote-empty-state" role="status" aria-label="QuickQuote products pending submit">
      <h3>Submit details to view products</h3>
      <p>Sarah can enter pricing facts on the left, then use Find Products to reveal mortgage options. Borrower last name and loan number stay in save/retrieve dialogs.</p>
      <div className="quickquote-reason-chips" aria-label="QuickQuote submit status">
        <span>{missingFacts > 0 ? `Missing facts: ${missingFacts}` : 'Pricing facts ready'}</span>
        <span>Products hidden until submit</span>
      </div>
    </section>
  );
}

function QuickQuoteEmptyState({ missingFacts }: { missingFacts: number }) {
  return (
    <section className="quickquote-empty-state" role="status" aria-label="QuickQuote empty state">
      <h3>No products displayed</h3>
      <div className="quickquote-reason-chips" aria-label="Empty state reason categories">
        <span>Products: none</span>
        <span>{missingFacts > 0 ? `Missing facts: ${missingFacts}` : 'Pricing facts ready'}</span>
        <span>Pricing outputs: unavailable</span>
      </div>
      <div className="quickquote-empty-state__actions" aria-label="Empty state next actions">
        <span className="quote-status-pill quote-status-pill--pending">Complete borrower facts</span>
        <span className="quote-status-pill quote-status-pill--pending">Save draft from the action bar</span>
      </div>
    </section>
  );
}

function productStatusCounts(products: AuthorizedProduct[]) {
  return products.reduce((acc, product) => {
    if (product.status === 'ACTIVE') acc.eligible += 1;
    else if (product.status === 'PENDING') acc.refer += 1;
    else acc.ineligible += 1;
    return acc;
  }, { eligible: 0, refer: 0, ineligible: 0 });
}

function borrowerContextLabel(values: BorrowerIntake) {
  const joined = [values.borrowerFirstName, values.borrowerLastName].filter((value) => value.trim()).join(' ').trim();
  return joined || values.borrowerName || 'Borrower pending';
}

function loanContextLabel(values: BorrowerIntake) {
  return values.loanNumber || values.externalLoanId || values.scenarioName || 'Loan pending';
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
  return ['borrowerLastName', 'loanNumber', 'mortgageType'].includes(field);
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
  const seen = new Set<string>();
  return sections
    .flatMap(({ fields }) => fields)
    .filter((field) => {
      const candidateId = String(field.fieldId);
      if (candidateId === fieldId || seen.has(candidateId) || isHeaderMetadataField(field) || !configuredBuilderParentActive(field)) return false;
      seen.add(candidateId);
      return true;
    });
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
  if (isSaveRetrieveIdentifierField(String(field.fieldId))) return false;
  const record = field as ScenarioIntakeField & { pipelineRequired?: boolean };
  return record.pipelineRequired ?? field.required;
}

function isSaveRetrieveIdentifierField(fieldId: string): fieldId is SaveRetrieveIdentifierField {
  return (saveRetrieveIdentifierFields as string[]).includes(fieldId);
}

function identifierKeysFromValues(values: BorrowerIntake) {
  return {
    borrowerLastName: values.borrowerLastName ?? '',
    loanNumber: values.loanNumber ?? '',
  };
}

function firstMissingIdentifierError(keys: { borrowerLastName: string; loanNumber: string }, fields: SaveRetrieveIdentifierField[]) {
  const field = fields.find((candidate) => !keys[candidate].trim());
  if (!field) return null;
  return { field, message: `${minimumStartFieldLabel(field)} is required to save this pipeline.` };
}

function focusModalIdentifierField(action: 'save' | 'retrieve', field: SaveRetrieveIdentifierField) {
  document.getElementById(`pipeline-${action}-${String(field)}`)?.focus();
}

function isBorrowerIntakeField(fieldId: string): fieldId is keyof BorrowerIntake {
  return [...loanPassQuoteIntakeFields].includes(fieldId as typeof loanPassQuoteIntakeFields[number]);
}

function hasAnyValue(values: BorrowerIntake) {
  return Object.values(values).some((value) => value.trim());
}

function fieldForId(sections: Array<{ step: QuoteIntakeStepDefinition; fields: ScenarioIntakeField[] }>, fieldId: keyof BorrowerIntake) {
  return sections.flatMap(({ fields }) => fields).find((field) => field.fieldId === fieldId);
}

function quickQuotePricingFields(sections: Array<{ step: QuoteIntakeStepDefinition; fields: ScenarioIntakeField[] }>) {
  const fieldIds: Array<keyof BorrowerIntake> = ['channel', 'loanPurpose', 'mortgageType', 'baseLoanAmount', 'decisionCreditScore'];
  return fieldIds.map((fieldId) => {
    const configured = fieldForId(sections, fieldId);
    return {
      ...(configured ?? fallbackPricingField(fieldId)),
      label: quickQuotePricingFieldLabel(fieldId, configured?.label),
      required: fieldId === 'channel' || fieldId === 'loanPurpose' || fieldPipelineRequired(configured ?? fallbackPricingField(fieldId)),
      pipelineRequired: fieldId === 'channel' || fieldId === 'loanPurpose' || fieldPipelineRequired(configured ?? fallbackPricingField(fieldId)),
    } as ScenarioIntakeField;
  });
}

function mergeRequiredPricingFields(configuredFields: PipelineRequiredField[], pricingFields: ScenarioIntakeField[]): PipelineRequiredField[] {
  const seen = new Set(configuredFields.map((field) => String(field.fieldId)));
  const requiredQuickQuoteFields = pricingFields.filter((field): field is PipelineRequiredField => isBorrowerIntakeField(String(field.fieldId)) && fieldPipelineRequired(field) && !seen.has(String(field.fieldId)));
  return [...configuredFields, ...requiredQuickQuoteFields];
}

function quickQuotePricingFieldLabel(fieldId: keyof BorrowerIntake, configuredLabel?: string) {
  if (fieldId === 'channel') return 'Channel';
  if (fieldId === 'loanPurpose') return 'Loan purpose';
  return configuredLabel ?? minimumStartFieldLabel(fieldId);
}

function fallbackPricingField(fieldId: keyof BorrowerIntake): ScenarioIntakeField {
  return {
    fieldId,
    label: quickQuotePricingFieldLabel(fieldId),
    groupId: 'quickquote-pricing-inputs',
    dataType: fieldId === 'baseLoanAmount' || fieldId === 'decisionCreditScore' ? 'number' : 'text',
    required: fieldId === 'channel' || fieldId === 'loanPurpose',
    helpText: 'QuickQuote pricing input.',
    sourceRef: 'quickquote-pricing-inputs',
    decisionQuality: 'VERIFIED',
    validationMessages: [],
  };
}

function fallbackDropdownConfigState(message: string): DropdownConfigState {
  return { kind: 'fallback', options: fallbackTenantDropdownOptions(), message };
}

function applicationFieldsUnavailableText(message: string) {
  const detail = businessFacingText(message).trim();
  if (!detail || detail === 'Not provided' || /setup support is needed/i.test(detail)) {
    return 'Application fields are unavailable. Setup support is needed.';
  }
  return `Application fields are unavailable. ${detail}`;
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
    loanPurpose: withPlaceholder(resolved.loanPassEnums.loanPurpose ?? [], 'Select loan purpose'),
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

function availableFiltersForOffers(offers: OfferSummary[]): TenantProductsResponse['availableFilters'] {
  return {
    productTypes: uniqueSorted(offers.map((offer) => valueOrNa(offer.productFamily ?? offer.productLabel)).filter((value) => value !== 'N/A')),
    investors: uniqueSorted(offers.map((offer) => valueOrNa(offer.investor)).filter((value) => value !== 'N/A')),
    channels: uniqueSorted(['LoanHouse/LoanPass']),
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
    loanPurpose: toOptions(['Purchase', 'Rate/Term Refinance', 'Cash-Out Refinance']),
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

function filterLiveOffers(offers: OfferSummary[], filters: ProductFilterState) {
  const minRate = numberFromFilter(filters.rateMin);
  const maxRate = numberFromFilter(filters.rateMax);
  return offers.filter((offer) => {
    const productFamily = valueOrNa(offer.productFamily ?? offer.productLabel);
    const investor = valueOrNa(offer.investor);
    const status = offerStatusText(offer);
    const rate = numericOfferValue(offer.rate);
    const typeMatches = !filters.productType || normalized(productFamily) === normalized(filters.productType);
    const investorMatches = !filters.investor || normalized(investor) === normalized(filters.investor);
    const channelMatches = !filters.channel || normalized(filters.channel) === normalized('LoanHouse/LoanPass');
    const statusMatches = !filters.status || normalized(status).includes(normalized(filters.status));
    const minRateMatches = minRate == null || rate == null || rate >= minRate;
    const maxRateMatches = maxRate == null || rate == null || rate <= maxRate;
    return typeMatches && investorMatches && channelMatches && statusMatches && minRateMatches && maxRateMatches;
  });
}

function offerStatusCounts(offers: OfferSummary[]) {
  return offers.reduce((acc, offer) => {
    const status = normalized(offerStatusText(offer));
    if (status.includes('approved') || status.includes('eligible') || status.includes('available')) acc.eligible += 1;
    else if (status.includes('reject') || status.includes('ineligible') || status.includes('blocked')) acc.ineligible += 1;
    else acc.refer += 1;
    return acc;
  }, { eligible: 0, refer: 0, ineligible: 0 });
}

function offerStatusText(offer: OfferSummary) {
  return valueOrNa(offer.eligibilityStatus ?? offer.confidence ?? offer.explanationStatus ?? offer.scenarioFlags?.[0]).replace(/_/g, ' ');
}

function offerStatusClass(offer: OfferSummary) {
  const status = normalized(offerStatusText(offer));
  if (status.includes('approved') || status.includes('eligible') || status.includes('available')) return 'active';
  if (status.includes('reject') || status.includes('ineligible') || status.includes('blocked')) return 'inactive';
  return 'pending';
}

function lockTextForOffer(offer: OfferSummary) {
  const options = offer.lockPeriodOptions?.length ? offer.lockPeriodOptions.join(', ') : '';
  return valueOrNa(offer.lockPeriodDays ?? options ?? offer.lockEligibilityRefs?.[0]);
}

function sourceRefsForOffer(offer: OfferSummary) {
  return uniqueStrings([
    'LoanHouse/LoanPass via pricing-bff',
    ...(offer.upstreamRefs ?? []),
    ...(offer.snapshotRefs ?? []),
    ...(offer.auditIds ?? []),
    ...(offer.rateRefs ?? []),
    ...(offer.lockEligibilityRefs ?? []),
  ]).slice(0, 6);
}

function uniqueStrings(values: Array<string | null | undefined>) {
  return Array.from(new Set(values.filter((value): value is string => typeof value === 'string' && value.trim().length > 0)));
}

function uniqueSorted(values: string[]) {
  return uniqueStrings(values).sort((left, right) => left.localeCompare(right));
}

function offerField(offer: OfferSummary, fields: string[]) {
  const record = offer as OfferSummary & Record<string, unknown>;
  for (const field of fields) {
    const value = record[field];
    if (value !== null && value !== undefined && value !== '') return value;
  }
  return null;
}

function valueOrNa(value: unknown) {
  if (value === null || value === undefined || value === '') return 'N/A';
  if (typeof value === 'number') return Number.isFinite(value) ? String(value) : 'N/A';
  return String(value);
}

function numericOfferValue(value: unknown) {
  if (typeof value === 'number') return Number.isFinite(value) ? value : null;
  if (typeof value !== 'string') return null;
  const parsed = Number(value.replace(/[%,$]/g, ''));
  return Number.isFinite(parsed) ? parsed : null;
}

async function fetchLiveOfferComparison(tenantId: string, runId: string, fetchImpl: typeof fetch = fetch): Promise<OfferComparisonView> {
  const response = await fetchImpl(`/api/v1/tenants/${encodeURIComponent(tenantId)}/quote-runs/${encodeURIComponent(runId)}/offers`, {
    headers: {
      Accept: 'application/json',
      'X-Ui-Trace-Id': 'sarah-live-offers-ui-fix',
    },
  });
  if (response.status >= 500) throw new Error('BFF offer comparison boundary is temporarily unavailable.');
  const raw = objectRecord(await response.json());
  const rawOffers = arrayRecord(raw.offers) ?? arrayRecord(raw.options) ?? arrayRecord(raw.rankedOptions) ?? arrayRecord(raw.rows) ?? [];
  const offers = rawOffers.map((offer, index) => normalizeLiveOffer(offer, index)).filter((offer): offer is OfferSummary => Boolean(offer));
  return {
    runId: valueOrNa(raw.runId ?? raw.quoteId ?? runId),
    status: valueOrNa(raw.status ?? (offers.length > 0 ? 'QUOTE_SERVICE_LOANHOUSE_OFFERS_VISIBLE' : 'NO_OFFERS')),
    offers,
    sortOptions: stringValues(raw.sortOptions).length > 0 ? stringValues(raw.sortOptions) : ['rank', 'rate', 'payment', 'confidence'],
    selectedOfferId: valueOrEmpty(raw.selectedOfferId) || null,
    commitBlocked: typeof raw.commitBlocked === 'boolean' ? raw.commitBlocked : offers.length === 0,
    fallbackReason: valueOrEmpty(raw.fallbackReason) || null,
    requiredFacts: stringValues(raw.requiredFacts),
    backendRefs: stringValues(raw.backendRefs),
    uiTraceId: valueOrEmpty(raw.uiTraceId) || 'sarah-live-offers-ui-fix',
    events: stringValues(raw.events),
  };
}

function normalizeLiveOffer(raw: unknown, index: number): OfferSummary | null {
  const value = objectRecord(raw);
  const offerId = valueOrEmpty(value.offerId ?? value.optionId ?? value.id ?? value.productId ?? value.productCode);
  if (!offerId) return null;
  const normalized = {
    ...value,
    offerId,
    rank: numericOfferValue(value.rank) ?? index + 1,
    productLabel: valueOrEmpty(value.productLabel ?? value.productName ?? value.product ?? value.productId ?? value.productCode) || offerId,
    productFamily: valueOrEmpty(value.productFamily ?? value.productType ?? value.productCategory) || null,
    investor: valueOrEmpty(value.investor ?? value.investorName ?? value.investorLabel ?? value.investorId ?? value.investorCode) || null,
    rate: nullableLiveValue(value.rate ?? value.noteRate ?? value.interestRate),
    payment: nullableLiveValue(value.payment ?? value.monthlyPayment ?? value.monthlyPi ?? value.monthlyPI),
    apr: nullableLiveValue(value.apr),
    confidence: nullableLiveValue(value.confidence ?? value.status ?? value.approvalStatus ?? value.eligibilityStatus),
    rankScore: nullableLiveValue(value.rankScore ?? value.score ?? `rank:${index + 1}`),
    lockPeriodDays: nullableLiveValue(value.lockPeriodDays ?? value.lockDays ?? value.lockPeriod),
    eligibilityStatus: valueOrEmpty(value.eligibilityStatus ?? value.approvalStatus ?? value.status) || null,
    rationaleChips: stringValues(value.rationaleChips).concat(stringValues(value.rankReasons), stringValues(value.warnings)),
    scenarioFlags: stringValues(value.scenarioFlags).concat(valueOrEmpty(value.status) ? [valueOrEmpty(value.status)] : []),
    explanationStatus: valueOrEmpty(value.explanationStatus ?? value.status) || 'MISSING',
    commitBlocked: typeof value.commitBlocked === 'boolean' ? value.commitBlocked : undefined,
    requiredFacts: stringValues(value.requiredFacts),
    sourceScenarioId: valueOrEmpty(value.sourceScenarioId ?? value.scenarioId) || null,
    scenarioVersion: numericOfferValue(value.scenarioVersion),
    upstreamRefs: stringValues(value.upstreamRefs),
    lockEligibilityRefs: stringValues(value.lockEligibilityRefs),
    snapshotRefs: stringValues(value.snapshotRefs),
    auditIds: stringValues(value.auditIds),
    explanationSections: stringValues(value.explanationSections),
    productRuleRefs: stringValues(value.productRuleRefs),
    stipulationRefs: stringValues(value.stipulationRefs),
    rateRefs: stringValues(value.rateRefs),
    lockPeriodOptions: stringValues(value.lockPeriodOptions),
  } as OfferSummary & Record<string, unknown>;
  return normalized;
}

function objectRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {};
}

function arrayRecord(value: unknown): unknown[] | null {
  return Array.isArray(value) ? value : null;
}

function stringValues(value: unknown): string[] {
  if (!Array.isArray(value)) return [];
  return value.map(valueOrEmpty).filter(Boolean);
}

function valueOrEmpty(value: unknown) {
  const text = valueOrNa(value);
  return text === 'N/A' ? '' : text;
}

function nullableLiveValue(value: unknown): string | number | null {
  if (typeof value === 'number' && Number.isFinite(value)) return value;
  const text = valueOrEmpty(value);
  return text || null;
}

function loadCachedLiveRunId(tenantId: string) {
  try {
    return window.sessionStorage.getItem(`${lastLiveQuoteRunStoragePrefix}${tenantId}`) || '';
  } catch {
    return '';
  }
}

function loadCachedLiveOfferState(tenantId: string): LiveOfferState {
  try {
    const raw = window.sessionStorage.getItem(`${liveQuoteOffersStoragePrefix}${tenantId}`);
    if (!raw) return { kind: 'idle' };
    const parsed = JSON.parse(raw) as Partial<{ runId: string; comparison: OfferComparisonView }>;
    if (!parsed.runId || !parsed.comparison?.offers) return { kind: 'idle' };
    return { kind: 'loaded', runId: parsed.runId, comparison: parsed.comparison };
  } catch {
    return { kind: 'idle' };
  }
}

function cacheLiveOfferState(tenantId: string, state: Extract<LiveOfferState, { kind: 'loaded' }>) {
  try {
    window.sessionStorage.setItem(`${lastLiveQuoteRunStoragePrefix}${tenantId}`, state.runId);
    window.sessionStorage.setItem(`${liveQuoteOffersStoragePrefix}${tenantId}`, JSON.stringify({ runId: state.runId, comparison: state.comparison }));
  } catch {
    // Session storage is optional; live offers remain available in component state.
  }
}

function isProductEligible(product: AuthorizedProduct) {
  return product.status === 'ACTIVE';
}

function productAvailabilityLabel(product: AuthorizedProduct) {
  if (isProductEligible(product)) return 'Eligible product';
  if (product.status === 'PENDING') return 'Referable product - review reason available';
  return `${friendlyLabel(product.status)} product - row actions unavailable`;
}

function productUnavailableReason(product: AuthorizedProduct) {
  if (isProductEligible(product)) return 'Product is eligible for row actions.';
  if (product.status === 'PENDING') return 'Referable state is visible; pricing review can continue when connected approval details are available.';
  return 'Ineligible status remains visible; eligibility reason text is needed before policy detail can be shown.';
}

function productEligibilityReviewLabel(product: AuthorizedProduct) {
  if (isProductEligible(product)) return 'Eligible';
  if (product.status === 'PENDING') return 'Referable';
  return 'Ineligible';
}

function productAuditTraceRefs(product: AuthorizedProduct, metadata?: ScenarioIntakeMetadata | null): ProductMetricTraceRef[] {
  const productRecord = product as AuthorizedProduct & Record<string, unknown>;
  const metadataSettings = (metadata?.settings ?? {}) as Record<string, unknown>;
  const sources = [
    productRecord.traceReferences,
    productRecord.traceRefs,
    productRecord.auditTraceRefs,
    productRecord.auditEvidenceRefs,
    productRecord.traceEvidence,
    metadataSettings.quoteEconomicsTraceRefs,
    metadataSettings.productTraceRefs,
    metadataSettings.auditTraceRefs,
  ];
  const seen = new Set<string>();
  const refs = sources
    .flatMap((source) => normalizeTraceRefs(source, product.productCode))
    .filter((ref) => {
      const key = `${ref.label}:${ref.value}:${ref.href ?? ''}`;
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    });
  return refs.length > 0 ? refs : fallbackProductAuditTraceRefs(product);
}

function fallbackProductAuditTraceRefs(product: AuthorizedProduct): ProductMetricTraceRef[] {
  const productCode = product.productCode;
  return [
    { label: 'Eligibility', value: `eligibility-service:${productCode}:eligibility-state-pending`, metric: 'eligibility' },
    { label: 'Calculation', value: `pricing-service:${productCode}:calculation-output-pending`, metric: 'calculation' },
    { label: 'Adjustment', value: `adjustment-service:${productCode}:adjustment-output-pending`, metric: 'adjustment' },
    { label: 'Margin', value: `margin-service:${productCode}:margin-output-pending`, metric: 'margin' },
    { label: 'Pricing', value: `pricing-service:${productCode}:rate-output-pending`, metric: 'pricing' },
  ];
}

function normalizeTraceRefs(source: unknown, productCode: string): ProductMetricTraceRef[] {
  if (!source) return [];
  if (Array.isArray(source)) return source.flatMap((entry) => normalizeTraceRefEntry(entry, productCode));
  if (typeof source === 'object') {
    return Object.entries(source as Record<string, unknown>).flatMap(([key, entry]) => normalizeTraceRefEntry(entry, productCode, key));
  }
  return normalizeTraceRefEntry(source, productCode);
}

function normalizeTraceRefEntry(entry: unknown, productCode: string, fallbackLabel?: string): ProductMetricTraceRef[] {
  if (typeof entry === 'string') {
    const value = entry.trim();
    return value ? [{ label: traceLabel(fallbackLabel), value, metric: fallbackLabel ?? undefined }] : [];
  }
  if (!entry || typeof entry !== 'object') return [];
  const record = entry as Record<string, unknown>;
  const configuredProductCode = stringSetting(record.productCode) ?? stringSetting(record.product) ?? stringSetting(record.productRef);
  if (configuredProductCode && configuredProductCode !== productCode) return [];
  const value = stringSetting(record.value)
    ?? stringSetting(record.ref)
    ?? stringSetting(record.traceRef)
    ?? stringSetting(record.traceId)
    ?? stringSetting(record.id);
  if (!value) return [];
  const metric = stringSetting(record.metric) ?? stringSetting(record.kind) ?? stringSetting(record.type) ?? fallbackLabel;
  const label = stringSetting(record.label) ?? labelForTraceMetric(metric);
  const href = stringSetting(record.href) ?? stringSetting(record.route) ?? stringSetting(record.path);
  return [{ label, value, metric: metric ?? undefined, href: href ?? undefined }];
}

function labelForTraceMetric(metric: string | null | undefined) {
  if (!metric) return 'Trace';
  const normalizedMetric = normalized(metric);
  const labels: Record<string, string> = {
    rate: 'Pricing',
    price: 'Pricing',
    pricing: 'Pricing',
    adjustedrate: 'Pricing',
    adjustedprice: 'Pricing',
    payment: 'Calculation',
    dscr: 'Calculation',
    calculation: 'Calculation',
    fees: 'Fee',
    fee: 'Fee',
    llpa: 'LLPA',
    margin: 'Margin',
    eligibility: 'Eligibility',
  };
  return labels[normalizedMetric] ?? friendlyLabel(metric);
}

function traceLabel(label: string | null | undefined) {
  return labelForTraceMetric(label ?? 'Trace');
}

function actionLabel(action: ProductRowAction) {
  if (action === 'refresh') return 'pricing refresh';
  if (action === 'lock') return 'lock request';
  return 'status review';
}

function lockFieldBindingsForMetadata(metadata: ScenarioIntakeMetadata | null, values: BorrowerIntake): LockFieldBinding[] {
  const settings = metadata?.settings?.lockingFields;
  if (!settings) return [];
  const toUndef = (v: string | null): string | undefined => (v === null ? undefined : v);
  const fieldIds: [string, string | undefined][] = [
    ['Request date', toUndef(stringSetting(settings.requestDateFieldId) ?? stringSetting(settings.lockRequestDateFieldId))],
    ['Expiration date', toUndef(stringSetting(settings.expirationDateFieldId) ?? stringSetting(settings.lockExpirationDateFieldId))],
    ['Extension', toUndef(stringSetting(settings.extensionFieldId) ?? stringSetting(settings.extensionDaysFieldId) ?? stringSetting(settings.primaryExtensionFieldId))],
  ];
  const filtered = fieldIds
    .filter((entry): entry is [string, string] => Boolean(entry[1]));
  return filtered.map(([label, configuredFieldId]) => {
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

function normalizeConfiguredColumn(entry: string | { key?: string; fieldId?: string; label?: string; hidden?: boolean; visible?: boolean; order?: number; missingLabel?: string; blockedLabel?: string }, index: number, metadata: ReturnType<typeof normalizeMetadataState>['metadata']): (PriceScenarioColumn & { order: number }) | null {
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
  return (
    <div className="quote-intake-banner quote-intake-banner--success" role="status" aria-label="Pipeline quote launch details">
      <strong>Pipeline run created</strong>
      <span>Run ID: {state.launch.runId}</span>
      <span>Next step: {state.launch.nextRoute ?? 'Review offers'}</span>
      <dl className="quote-launch-details">
        {state.selectedProduct ? <><div><dt>Selected product</dt><dd>{state.selectedProduct.productName} ({state.selectedProduct.productCode})</dd></div><div><dt>Investor</dt><dd>{state.selectedProduct.investorCode}</dd></div><div><dt>Channel</dt><dd>{channelLabel(state.selectedProduct.channelCode)}</dd></div></> : null}
        <div><dt>Evaluation trace</dt><dd>{state.launch.uiTraceId || state.launch.replayHashRef || 'Trace pending from pricing response'}</dd></div>
        {state.launch.auditPackageId ? <div><dt>Audit package</dt><dd>{state.launch.auditPackageId}</dd></div> : null}
      </dl>
    </div>
  );
}

export default PipelineIntakePage;
