export type BorrowerIntake = {
  channel: string;
  channelCode: string;
  channelType: string;
  loanNumber: string;
  borrowerFirstName: string;
  borrowerLastName: string;
  numberOfBorrowers: string;
  contactEmail: string;
  coBorrowerName: string;
  coBorrowerRole: string;
  decisionCreditScore: string;
  creditScoreType: string;
  citizenshipType: string;
  professional: string;
  firstTimeHomeBuyer: string;
  firstTimeInvestor: string;
  loanPurpose: string;
  baseLoanAmount: string;
  purchasePrice: string;
  appraisedValue: string;
  downPayment: string;
  percentDownpayment: string;
  loanToValue: string;
  combinedLoanToValue: string;
  cashOutAmount: string;
  transactionType: string;
  loanBalance: string;
  firstLoanBalance: string;
  secondLoanBalance: string;
  firstLienAmount: string;
  secondLienAmount: string;
  lienPosition: string;
  refinancingType: string;
  desiredLoanTerm: string;
  loanTermType: string;
  desiredAmortizationType: string;
  desiredRateLockPeriod: string;
  lockPeriodType: string;
  desiredInterestRate: string;
  prepaymentPenaltyTerm: string;
  prePaymentPenaltyStructureType: string;
  waiveEscrows: string;
  interestOnly: string;
  mortgageType: string;
  investorCode: string;
  loanQualificationType: string;
  mortgageInsuranceType: string;
  miOptionType: string;
  gift: string;
  achPayment: string;
  wholesaleCompensation: string;
  compensationPaidType: string;
  vaLoanType: string;
  vaFundingFeeExemptionType: string;
  vaFirstTimeUse: string;
  state: string;
  zip: string;
  countyFips: string;
  countyCode: string;
  countyName: string;
  city: string;
  street: string;
  addressSearchString: string;
  propertyType: string;
  propertyInformationType: string;
  occupancyType: string;
  numberOfUnits: string;
  propertyLocation: string;
  numberOfLeasedUnits: string;
  shortTermRental: string;
  propertySquareFootage: string;
  propertyAcreageNumber: string;
  condoApprovalType: string;
  acquisitionDate: string;
  monthlyMarketRent: string;
  propertyRentalIncome: string;
  rentalIncomeMightBeUsed: string;
  investorExperience: string;
  additionalMonthlyHousingExpenses: string;
  additionalAnnualHousingExpenses: string;
  monthlyTaxes: string;
  monthlyInsurance: string;
  monthlyHOA: string;
  annualTaxes: string;
  annualInsurance: string;
  annualHOA: string;
  selfEmployed: string;
  selfEmployedTimeFrame: string;
  totalBorrowerIncome: string;
  monthlyDebt: string;
  totalLiabilityMonthlyPayment: string;
  estimatedDti: string;
  estimatedDSCR: string;
  monthsOfReserves: string;
  liquidAssets: string;
  documentationType: string;
  incomeDocumentationType: string;
  secondaryDocumentationType: string;
  documentationTypeTimeFrame: string;
  mortgageLatePayments: string;
  mortgageHistoryDesc: string;
  creditEvent: string;
  chapter7BankruptcyDate: string;
  chapter11BankruptcyDate: string;
  chapter13BankruptcyDate: string;
  deedInLieuDate: string;
  foreclosureDate: string;
  shortSaleDate: string;
  mortgageModificationDate: string;
  forbearanceDate: string;
  lockExtension: string;
  lockExtension2: string;
  concession: string;
  secondaryAdjustment: string;
  aus: string;
  manualUnderwriting: string;

  /** Optional compatibility aliases accepted from older drafts; LoanPass launch payloads normalize or exclude them. */
  quoteIntent: string;
  scenarioName: string;
  externalLoanId: string;
  sourceSystem: string;
  borrowerName: string;
  borrowerRole: string;
  creditStatus: string;
  creditScore: string;
  creditScoreSource: string;
  creditReportDate: string;
  creditReadiness: string;
  loanAmount: string;
  purchasePriceOrValue: string;
  propertyZip: string;
  termMonths: string;
  amortizationType: string;
  requestedLockPeriodDays: string;
  propertyState: string;
  propertyCounty: string;
  unitCount: string;
  condoProjectType: string;
  manufacturedHomeFlag: string;
  monthlyIncome: string;
  incomeType: string;
  employmentType: string;
  suppliedDti: string;
  reserveMonths: string;
  incomeVerificationStatus: string;
  assetVerificationStatus: string;
  productFamily: string;
  productPreference: string;
  quoteFilters: string;
  effectiveDate: string;
  actorId: string;
  clientContext: string;
  downPaymentOrEquity: string;
  subordinateFinancingAmount: string;
  helocDrawnAmount: string;
  helocLimitAmount: string;
  reserves: string;
};

export const loanPassQuoteIntakeFields = [
  'channel', 'channelCode', 'channelType', 'loanNumber', 'borrowerFirstName', 'borrowerLastName', 'numberOfBorrowers', 'contactEmail', 'decisionCreditScore', 'creditScoreType', 'citizenshipType', 'professional', 'firstTimeHomeBuyer', 'firstTimeInvestor',
  'loanPurpose', 'baseLoanAmount', 'purchasePrice', 'appraisedValue', 'downPayment', 'percentDownpayment', 'loanToValue', 'combinedLoanToValue', 'cashOutAmount', 'transactionType', 'loanBalance', 'firstLoanBalance', 'secondLoanBalance', 'firstLienAmount', 'secondLienAmount', 'lienPosition', 'refinancingType',
  'desiredLoanTerm', 'loanTermType', 'desiredAmortizationType', 'desiredRateLockPeriod', 'lockPeriodType', 'desiredInterestRate', 'prepaymentPenaltyTerm', 'prePaymentPenaltyStructureType', 'waiveEscrows', 'interestOnly', 'mortgageType', 'investorCode', 'loanQualificationType', 'mortgageInsuranceType', 'miOptionType', 'gift', 'achPayment', 'wholesaleCompensation', 'compensationPaidType', 'vaLoanType', 'vaFundingFeeExemptionType', 'vaFirstTimeUse',
  'state', 'zip', 'countyFips', 'countyCode', 'countyName', 'city', 'street', 'addressSearchString', 'propertyType', 'propertyInformationType', 'occupancyType', 'numberOfUnits', 'propertyLocation', 'numberOfLeasedUnits', 'shortTermRental', 'propertySquareFootage', 'propertyAcreageNumber', 'condoApprovalType', 'acquisitionDate',
  'monthlyMarketRent', 'propertyRentalIncome', 'rentalIncomeMightBeUsed', 'investorExperience', 'additionalMonthlyHousingExpenses', 'additionalAnnualHousingExpenses', 'monthlyTaxes', 'monthlyInsurance', 'monthlyHOA', 'annualTaxes', 'annualInsurance', 'annualHOA',
  'selfEmployed', 'selfEmployedTimeFrame', 'totalBorrowerIncome', 'monthlyDebt', 'totalLiabilityMonthlyPayment', 'estimatedDti', 'estimatedDSCR', 'monthsOfReserves', 'liquidAssets', 'documentationType', 'incomeDocumentationType', 'secondaryDocumentationType', 'documentationTypeTimeFrame', 'mortgageLatePayments', 'mortgageHistoryDesc', 'creditEvent', 'chapter7BankruptcyDate', 'chapter11BankruptcyDate', 'chapter13BankruptcyDate', 'deedInLieuDate', 'foreclosureDate', 'shortSaleDate', 'mortgageModificationDate', 'forbearanceDate',
  'lockExtension', 'lockExtension2', 'concession', 'secondaryAdjustment', 'aus', 'manualUnderwriting',
] as const satisfies ReadonlyArray<keyof BorrowerIntake>;

export type LoanPassQuoteIntakePayload = Partial<Record<keyof BorrowerIntake, string>>;

export function toLoanPassQuoteIntakePayload(intake: BorrowerIntake): LoanPassQuoteIntakePayload {
  const aliases: Partial<Record<keyof BorrowerIntake, string>> = {
    zip: intake.zip || intake.propertyZip || '',
    loanNumber: intake.loanNumber || intake.externalLoanId || '',
    baseLoanAmount: intake.baseLoanAmount || intake.loanAmount || '',
    appraisedValue: intake.appraisedValue || intake.purchasePriceOrValue || '',
    desiredLoanTerm: intake.desiredLoanTerm || intake.termMonths || '',
    loanTermType: intake.loanTermType || intake.termMonths || intake.desiredLoanTerm || '',
    desiredAmortizationType: intake.desiredAmortizationType || intake.amortizationType || '',
    desiredRateLockPeriod: intake.desiredRateLockPeriod || intake.requestedLockPeriodDays || '',
    channelCode: intake.channelCode || intake.channel || '',
    channelType: intake.channelType || intake.channelCode || intake.channel || '',
    documentationType: intake.documentationType || intake.incomeDocumentationType || intake.incomeType || '',
    incomeDocumentationType: intake.incomeDocumentationType || intake.documentationType || intake.incomeType || '',
    selfEmployed: intake.selfEmployed || (normalizedValue(intake.employmentType ?? '') === 'selfemployed' ? 'Yes' : intake.employmentType ? 'No' : ''),
    totalBorrowerIncome: intake.totalBorrowerIncome || intake.monthlyIncome || '',
    estimatedDti: intake.estimatedDti || intake.suppliedDti || '',
    monthsOfReserves: intake.monthsOfReserves || intake.reserveMonths || intake.reserves || '',
    decisionCreditScore: intake.decisionCreditScore || intake.creditScore || '',
    numberOfUnits: intake.numberOfUnits || intake.unitCount || '',
    propertyInformationType: intake.propertyInformationType || intake.propertyType || '',
    mortgageType: intake.mortgageType || intake.productFamily || '',
    downPayment: intake.downPayment || intake.downPaymentOrEquity || '',
    secondLienAmount: intake.secondLienAmount || intake.subordinateFinancingAmount || '',
    propertyRentalIncome: intake.propertyRentalIncome || intake.monthlyMarketRent || '',
    totalLiabilityMonthlyPayment: intake.totalLiabilityMonthlyPayment || intake.monthlyDebt || '',
    compensationPaidType: intake.compensationPaidType || intake.wholesaleCompensation || '',
  };

  return Object.fromEntries(
    loanPassQuoteIntakeFields.map((field) => [field, aliases[field] ?? intake[field] ?? '']),
  ) as LoanPassQuoteIntakePayload;
}

function normalizedValue(value: string) {
  return value.toLowerCase().replace(/[^a-z0-9]/g, '');
}

export type ScenarioIntakeField = {
  fieldId: keyof BorrowerIntake;
  label: string;
  groupId: string;
  dataType: 'text' | 'string' | 'email' | 'textarea' | 'number' | 'enum' | 'date' | 'duration';
  required: boolean;
  helpText: string;
  helpTooltip?: string;
  showHelpIcon?: boolean;
  options?: DropdownOption[];
  enumOptions?: DropdownOption[];
  constraints?: ScenarioIntakeFieldConstraints;
  minimum?: string | number | null;
  maximum?: string | number | null;
  min?: string | number | null;
  max?: string | number | null;
  minValue?: string | number | null;
  maxValue?: string | number | null;
  precision?: string | number | null;
  step?: string | number | null;
  units?: string | null;
  valueType?: 'input' | 'header' | string | ScenarioIntakeFieldValueType;
  displayType?: string;
  fieldType?: string;
  order?: number;
  displayOrder?: number;
  sortOrder?: number;
  active?: boolean;
  visible?: boolean;
  removed?: boolean;
  isRemoved?: boolean;
  visibilityCondition?: ScenarioIntakeFieldVisibilityCondition;
  sourceRef: string;
  decisionQuality: 'VERIFIED' | 'UNKNOWN' | 'CONFLICTING';
  validationMessages: string[];
};

export type ScenarioIntakeFieldConstraints = Partial<Record<'minimum' | 'maximum' | 'min' | 'max' | 'minValue' | 'maxValue' | 'precision' | 'step' | 'increment' | 'units', string | number | null>>;

export type ScenarioIntakeFieldValueType = {
  type?: 'text' | 'string' | 'email' | 'textarea' | 'number' | 'enum' | 'date' | 'duration' | string;
  enumTypeId?: string | null;
  options?: DropdownOption[];
  enumOptions?: DropdownOption[];
  variants?: Array<DropdownOption | { id?: string; variantId?: string; value?: string; label?: string; name?: string }>;
  minimum?: string | number | null;
  maximum?: string | number | null;
  min?: string | number | null;
  max?: string | number | null;
  minValue?: string | number | null;
  maxValue?: string | number | null;
  precision?: string | number | null;
  step?: string | number | null;
  increment?: string | number | null;
  units?: string | null;
};

export type ScenarioIntakeFieldVisibilityCondition = {
  parentFieldId: keyof BorrowerIntake;
  operator: 'equals' | 'not_equals' | 'any_of' | 'contains' | 'greater_than' | 'less_than' | 'empty' | 'not_empty';
  values: string[];
};

export type DropdownOption = {
  value: string;
  label: string;
};

export type TenantDropdownOptions = {
  productTypes: DropdownOption[];
  investors: DropdownOption[];
  channels: DropdownOption[];
  loanPassEnums: Partial<Record<keyof BorrowerIntake, DropdownOption[]>>;
  source: 'tenant-config' | 'tenant-products' | 'fallback';
};

export type ScenarioIntakeFieldGroup = {
  groupId: string;
  label: string;
  helpText: string;
  fields: ScenarioIntakeField[];
};

export type ScenarioIntakeValidationIssue = {
  code: string;
  fieldPath: string;
  severity: 'BLOCKING' | 'WARNING';
  message: string;
};

export type ScenarioIntakeMetadata = {
  tenantContext: string;
  dependencyStatus: string;
  fieldGroups: ScenarioIntakeFieldGroup[];
  decisionControls: string[];
  validationIssues: ScenarioIntakeValidationIssue[];
  auditPackageId: string;
  replayHashRef: string;
  fallbackReason: string;
  uiTraceId: string;
  quickQuoteState?: ProgressiveQuickQuoteState;
  settings?: {
    priceScenarioTable?: {
      columns?: Array<string | { key: string; label?: string; hidden?: boolean; visible?: boolean; order?: number }>;
      extraColumns?: Array<string | { key?: string; fieldId?: string; label?: string; hidden?: boolean; visible?: boolean; order?: number; missingLabel?: string; blockedLabel?: string }>;
      hiddenColumns?: string[];
      columnOrder?: string[];
    };
    pipelineFields?: Array<{ fieldId: string; label?: string; hidden?: boolean; visible?: boolean; status?: string; value?: string | number | null; missingLabel?: string; blockedLabel?: string }>;
    fieldPermissions?: {
      hiddenFields?: string[];
      deniedFields?: string[];
      visibleFields?: string[];
      [key: string]: unknown;
    };
    lockingFields?: {
      requestDateFieldId?: string;
      lockRequestDateFieldId?: string;
      expirationDateFieldId?: string;
      lockExpirationDateFieldId?: string;
      extensionFieldId?: string;
      extensionDaysFieldId?: string;
      primaryExtensionFieldId?: string;
      [key: string]: unknown;
    };
    autoConfirmation?: {
      lock?: boolean;
      reprice?: boolean;
      relock?: boolean;
      cancellation?: boolean;
      approvalPath?: string;
      disabledApprovalPath?: string;
      [key: string]: unknown;
    };
    applicationFormRuntime?: {
      source: 'published';
      versionLabel: string;
    };
  };
};

export type ProgressiveQuickQuoteState = {
  minimalFirstStepFields: string[];
  progressiveSectionOrder: string[];
  quoteServiceRequiredFacts: string[];
  backendOwnedFactSources: string[];
  blockedByContracts: string[];
  fallbackReason: string;
};

export type IntakeValidation = {
  passed: boolean;
  status: 'PASSED' | 'BLOCKED';
  message: string;
  blockers: Partial<Record<keyof BorrowerIntake, string>>;
};

export type QuoteRunLaunch = {
  runId: string | null;
  status: 'CREATED' | 'BLOCKED';
  nextRoute: string | null;
  validationSummary: IntakeValidation;
  uiTraceId: string;
  events: string[];
  fallbackMode: boolean;
  dependencyStatus: string;
  auditPackageId: string | null;
  replayHashRef: string | null;
  validationIssues: ScenarioIntakeValidationIssue[];
  backendFactRefs?: string[];
  missingContractBlockers?: string[];
  quickQuoteState?: ProgressiveQuickQuoteState | null;
};

export type QuoteLaunchSelectedProduct = {
  productCode: string;
  productName: string;
  investorCode: string;
  channelCode: string;
  productType: string;
};

export type LaunchState =
  | { kind: 'idle' }
  | { kind: 'submitting' }
  | { kind: 'blocked'; validation: IntakeValidation }
  | { kind: 'created'; launch: QuoteRunLaunch; selectedProduct?: QuoteLaunchSelectedProduct }
  | { kind: 'outage'; message: string };

export type MetadataState =
  | { kind: 'loading' }
  | { kind: 'loaded'; metadata: ScenarioIntakeMetadata }
  | { kind: 'unreachable'; message: string };

export type ApplicationFormRuntimeState =
  | { kind: 'loaded'; metadata: ScenarioIntakeMetadata; versionLabel: string }
  | { kind: 'missing'; message: string }
  | { kind: 'unreachable'; message: string };

type ApplicationFormRuntimeResponse = Partial<ScenarioIntakeMetadata> & {
  tenantId?: string;
  formId?: string;
  versionId?: string;
  version?: string | number;
  status?: string;
  fieldGroups?: ScenarioIntakeFieldGroup[];
  sections?: ScenarioIntakeFieldGroup[];
  form?: Partial<ScenarioIntakeMetadata> & { fieldGroups?: ScenarioIntakeFieldGroup[]; sections?: ScenarioIntakeFieldGroup[] };
};

export async function fetchActiveApplicationFormVersion(
  tenantId: string,
  fetchImpl: typeof fetch = fetch,
): Promise<ApplicationFormRuntimeState> {
  const response = await fetchImpl(`/api/v1/tenants/${encodeURIComponent(tenantId)}/application-forms/active`, {
    headers: {
      Accept: 'application/json',
      'X-Ui-Trace-Id': 'application-form-runtime-renderer',
    },
  });

  if (response.status === 404 || response.status === 204) {
    return { kind: 'missing', message: 'No published tenant application form version is configured. Configuration blocker recorded; runtime did not invent fields.' };
  }
  if (!response.ok) throw new Error('Published tenant application form version is temporarily unavailable.');

  const body = await response.json() as ApplicationFormRuntimeResponse;
  const candidate = body.form ?? body;
  const fieldGroups = candidate.fieldGroups ?? candidate.sections ?? [];
  if (!Array.isArray(fieldGroups) || fieldGroups.length === 0) {
    return { kind: 'missing', message: 'Published tenant application form version did not include field groups. Configuration blocker recorded; runtime did not invent fields.' };
  }

  return {
    kind: 'loaded',
    metadata: {
      tenantContext: candidate.tenantContext ?? body.tenantId ?? tenantId,
      dependencyStatus: candidate.dependencyStatus ?? 'READY',
      fieldGroups,
      decisionControls: candidate.decisionControls ?? [],
      validationIssues: candidate.validationIssues ?? [],
      auditPackageId: candidate.auditPackageId ?? '',
      replayHashRef: candidate.replayHashRef ?? '',
      fallbackReason: candidate.fallbackReason ?? '',
      uiTraceId: candidate.uiTraceId ?? 'application-form-runtime-renderer',
      quickQuoteState: candidate.quickQuoteState,
      settings: {
        ...candidate.settings,
        applicationFormRuntime: { source: 'published', versionLabel: String(body.versionId ?? body.version ?? body.formId ?? 'active') },
      },
    },
    versionLabel: String(body.versionId ?? body.version ?? body.formId ?? 'active'),
  };
}

export async function fetchTenantDropdownOptions(
  tenantId: string,
  fetchImpl: typeof fetch = fetch,
): Promise<TenantDropdownOptions> {
  try {
    const productOptions = await fetchTenantProductDropdownOptions(tenantId, fetchImpl);
    return {
      ...productOptions,
      loanPassEnums: {},
    };
  } catch {
    const catalogOptions = await fetchCatalogDropdownOptions(tenantId, fetchImpl);
    return {
      ...catalogOptions,
      loanPassEnums: {},
    };
  }
}

async function fetchTenantProductDropdownOptions(tenantId: string, fetchImpl: typeof fetch): Promise<Omit<TenantDropdownOptions, 'loanPassEnums'>> {
  const response = await fetchImpl(`/api/v1/tenants/${encodeURIComponent(tenantId)}/products?page=1&pageSize=500`, {
    headers: {
      Accept: 'application/json',
      'X-Ui-Trace-Id': 'pipeline-dropdown-config-trace',
    },
  });

  return normalizeTenantProductDropdowns(await readPipelineJson<unknown>(response, {
    endpointContext: 'tenant product dropdown configuration',
    unavailableMessage: 'Tenant product dropdown configuration is temporarily unavailable.',
  }));
}

function normalizeTenantProductDropdowns(payload: unknown): Omit<TenantDropdownOptions, 'loanPassEnums'> {
  const record = asRecord(payload);
  const filters = asRecord(record.availableFilters);
  const products = Array.isArray(record.products) ? record.products.filter(isRecord) : [];
  const productTypes = optionList(filters.productTypes, products.map((product) => product.productType));
  const investors = optionList(filters.investors, products.map((product) => product.investorCode));
  const channels = optionList(filters.channels, products.map((product) => product.channelCode));
  if (productTypes.length === 0 && investors.length === 0 && channels.length === 0) throw new Error('Tenant product dropdown configuration returned no selectable options.');
  return { productTypes, investors, channels, source: 'tenant-products' };
}

async function fetchCatalogDropdownOptions(tenantId: string, fetchImpl: typeof fetch): Promise<Omit<TenantDropdownOptions, 'loanPassEnums'>> {
  const [products, investors, channels] = await Promise.all([
    fetchCatalogPayload(`/api/v1/tenants/${encodeURIComponent(tenantId)}/product-catalog/products`, 'product catalog products', fetchImpl),
    fetchCatalogPayload(`/api/v1/tenants/${encodeURIComponent(tenantId)}/product-catalog/investors`, 'product catalog investors', fetchImpl),
    fetchCatalogPayload(`/api/v1/tenants/${encodeURIComponent(tenantId)}/product-catalog/channels`, 'product catalog channels', fetchImpl),
  ]);

  const productRecords = extractRecords(products, 'products');
  const investorRecords = extractRecords(investors, 'investors');
  const channelRecords = extractRecords(channels, 'channels');
  const productTypes = optionList(productRecords.map((product) => product.productType ?? product.type ?? product.productFamily));
  const investorOptions = optionList(investorRecords.map((investor) => investor.investorCode ?? investor.code ?? investor.id), productRecords.map((product) => product.investorCode));
  const channelOptions = optionList(channelRecords.map((channel) => channel.channelCode ?? channel.code ?? channel.id), productRecords.map((product) => product.channelCode));
  if (productTypes.length === 0 && investorOptions.length === 0 && channelOptions.length === 0) throw new Error('Catalog dropdown configuration returned no selectable options.');
  return { productTypes, investors: investorOptions, channels: channelOptions, source: 'tenant-config' };
}

async function fetchCatalogPayload(path: string, endpointContext: string, fetchImpl: typeof fetch): Promise<unknown> {
  const response = await fetchImpl(path, {
    headers: {
      Accept: 'application/json',
      'X-Ui-Trace-Id': 'pipeline-dropdown-config-trace',
    },
  });
  return readPipelineJson<unknown>(response, {
    endpointContext,
    unavailableMessage: `${endpointContext} is temporarily unavailable.`,
  });
}

function extractRecords(payload: unknown, key: string): Record<string, unknown>[] {
  if (Array.isArray(payload)) return payload.filter(isRecord);
  const record = asRecord(payload);
  const keyed = record[key];
  if (Array.isArray(keyed)) return keyed.filter(isRecord);
  const items = record.items;
  if (Array.isArray(items)) return items.filter(isRecord);
  return [];
}

function optionList(...sources: unknown[]): DropdownOption[] {
  const values = sources.flatMap((source) => Array.isArray(source) ? source : []).map(optionValue).filter((value): value is string => Boolean(value));
  return Array.from(new Set(values)).sort((left, right) => left.localeCompare(right)).map((value) => ({ value, label: friendlyOptionLabel(value) }));
}

function optionValue(value: unknown): string | null {
  if (typeof value === 'string') return value;
  const record = asRecord(value);
  const candidate = record.value ?? record.code ?? record.id ?? record.productType ?? record.investorCode ?? record.channelCode;
  return typeof candidate === 'string' ? candidate : null;
}

function asRecord(value: unknown): Record<string, unknown> {
  return isRecord(value) ? value : {};
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function friendlyOptionLabel(value: string) {
  return value.toLowerCase().split(/[_\s-]+/).filter(Boolean).map((part) => part[0].toUpperCase() + part.slice(1)).join(' ');
}

export type RedactedWaterfallValue = {
  value: string | null;
  redacted: boolean;
  reason: string | null;
  auditRef?: string | null;
};

export type WaterfallLedgerRow = {
  ordinal: number;
  section?: 'Base Rate' | 'Adjustments' | 'Margins' | 'Rounding' | string;
  step: string;
  inputValue: RedactedWaterfallValue;
  operation: string;
  outputValue: RedactedWaterfallValue;
  configRef: string;
  reasonCode: string;
  roundingMode: string | null;
  inputDetails?: string[];
  outputDetails?: string[];
  marginRefs?: string[];
  adjustmentRefs?: string[];
};

export type PricingWaterfallView = {
  tenantContext: string;
  runId: string;
  status: 'READY' | 'BLOCKED';
  restrictedValuesVisible: boolean;
  dependencyStatus: string;
  baseSelection: {
    selectionId: string;
    gridVersionRef: string;
    selectedNoteRate: RedactedWaterfallValue;
    basePrice: RedactedWaterfallValue;
    ledgerSteps: string[];
  };
  finalPrice: {
    finalPriceId: string;
    roundedFinalPrice: RedactedWaterfallValue;
    ledger: WaterfallLedgerRow[];
    adjustmentRefs: string[];
    marginRefs?: string[];
    roundingTraceRefs: string[];
    roundingMode?: string | null;
    precision?: string | null;
  };
  blockers: Array<{ code: string; message: string; sourceRef: string; remediation?: string }>;
  versionRefs: string[];
  auditRefs: string[];
  replayHash: string;
  versionGraphHash: string;
  resultHash: string;
  evidenceHash: string;
  uiTraceId: string;
  events: string[];
  fallbackReason: string;
};

export type JourneyFreshness = {
  status: string;
  evidenceRef: string;
  message: string;
};

export type JourneyDrilldownRefs = {
  runId: string;
  scenarioRef: string;
  quoteRef: string;
  lockRef: string;
  correlationRef: string;
};

export type QuoteJourneyNode = {
  nodeId: string;
  label: string;
  serviceName: string;
  status: 'VISIBLE_WITH_REFS' | 'VISIBLE_WITH_BLOCKERS' | 'BLOCKED' | 'UNAVAILABLE' | string;
  freshness: JourneyFreshness;
  evidenceRefs: string[];
  blockers: string[];
  replayHash: string;
  downstreamDependencies: string[];
  drilldownRoute: string;
  drilldownRefs: JourneyDrilldownRefs;
};

export type QuoteJourneyMapView = {
  tenantContext: string;
  runId: string;
  status: string;
  dependencyStatus: string;
  nodes: QuoteJourneyNode[];
  blockers: string[];
  serviceContracts: string[];
  uiTraceId: string;
  events: string[];
  fallbackReason: string;
};

export type LockWorkflowStatus = 'READY' | 'CONFIRMED' | 'EXPIRED' | 'EXTENDED' | 'RELOCKED' | 'FLOAT_DOWN' | 'BLOCKED';

export type LockWorkflowActionType = 'extend' | 'relock' | 'float_down';

export type LockWorkflowTerms = {
  productLabel: string;
  investor: string;
  channel: string;
  noteRate: string;
  finalPriceBps: string;
  lockPeriodDays: number;
  expiresAt: string;
  waterfallRef: string;
  adjustmentRefs: string[];
  marginRefs: string[];
  investorConfirmationRequired: boolean;
};

export type LockWorkflowDisclosure = {
  disclosureId: string;
  title: string;
  text: string;
  complianceRef: string;
};

export type LockWorkflowBlocker = {
  code: string;
  message: string;
  remediation: string;
  sourceRef: string;
};

export type LockWorkflowAction = {
  action: LockWorkflowActionType;
  label: string;
  eligible: boolean;
  fee: string | null;
  maxDays: number | null;
  approvalRequired: boolean;
  terms: string;
  blocker?: string | null;
};

export type LockWorkflowHistoryEvent = {
  eventId: string;
  eventType: string;
  timestamp: string;
  actor: string;
  terms: string;
  approvalRef: string | null;
  auditRef: string;
};

export type LockWorkflowView = {
  tenantContext: string;
  runId: string;
  selectedOfferId: string;
  status: LockWorkflowStatus;
  lockIdPreview: string;
  lockId: string | null;
  terms: LockWorkflowTerms;
  disclosures: LockWorkflowDisclosure[];
  lockDisabled: boolean;
  lockDisabledReason: string | null;
  blockers: LockWorkflowBlocker[];
  postLockActions: LockWorkflowAction[];
  history: LockWorkflowHistoryEvent[];
  uiTraceId: string;
  events: string[];
  fallbackReason: string;
};

export type LockConfirmationRequest = {
  selectedOfferId: string;
  action: 'confirm' | LockWorkflowActionType;
  disclosuresAccepted: boolean;
  disclosureScrollComplete: boolean;
  signatureName: string;
  signedAt: string;
};

export type LockConfirmationResult = {
  status: 'CONFIRMED' | 'CONFLICT' | 'BLOCKED';
  lockId: string | null;
  expiresAt: string | null;
  message: string;
  conflictResolution?: string | null;
  auditRef: string;
  historyEvent?: LockWorkflowHistoryEvent;
};

type PipelineResponseBody =
  | { kind: 'json'; value: unknown }
  | { kind: 'empty' }
  | { kind: 'non-json' };

type PipelineApiOptions = {
  endpointContext: string;
  unavailableMessage: string;
};

export class PipelineApiResponseError extends Error {
  readonly status: number;
  readonly endpointContext: string;

  constructor(status: number, endpointContext: string, message: string) {
    super(message);
    this.name = 'PipelineApiResponseError';
    this.status = status;
    this.endpointContext = endpointContext;
  }
}

async function readPipelineJson<T>(response: Response, options: PipelineApiOptions): Promise<T> {
  const body = await readPipelineResponseBody(response);
  const success = response.status >= 200 && response.status < 300;

  if (response.status >= 500 || (!success && body.kind !== 'json')) {
    throw pipelineResponseError(response.status, options, body);
  }

  if (body.kind !== 'json') {
    throw pipelineResponseError(response.status, options, body);
  }

  return body.value as T;
}

async function readPipelineResponseBody(response: Response): Promise<PipelineResponseBody> {
  if (typeof response.text === 'function') {
    const text = await response.text();
    if (!text.trim()) return { kind: 'empty' };
    try {
      return { kind: 'json', value: JSON.parse(text) };
    } catch {
      return { kind: 'non-json' };
    }
  }

  try {
    return { kind: 'json', value: await response.json() };
  } catch {
    return { kind: 'non-json' };
  }
}

function pipelineResponseError(status: number, options: PipelineApiOptions, body: PipelineResponseBody): PipelineApiResponseError {
  const reason = body.kind === 'empty' ? 'empty response body' : body.kind === 'non-json' ? 'non-JSON response body' : 'error response';
  const prefix = status >= 500 ? options.unavailableMessage : `${options.endpointContext} request failed.`;
  return new PipelineApiResponseError(status, options.endpointContext, `${prefix} Pipeline API returned HTTP ${status} for ${options.endpointContext} with ${reason}.`);
}

export async function fetchScenarioIntakeMetadata(
  tenantId: string,
  fetchImpl: typeof fetch = fetch,
): Promise<ScenarioIntakeMetadata> {
  const response = await fetchImpl(`/api/v1/tenants/${encodeURIComponent(tenantId)}/quote-runs/intake-metadata`, {
    headers: {
      Accept: 'application/json',
      'X-Ui-Trace-Id': 'brw-s01-local-trace',
    },
  });

  return readPipelineJson<ScenarioIntakeMetadata>(response, {
    endpointContext: 'scenario intake metadata',
    unavailableMessage: 'Scenario intake metadata is temporarily unavailable.',
  });
}

export async function launchQuoteRun(
  tenantId: string,
  intake: BorrowerIntake,
  fetchImpl: typeof fetch = fetch,
): Promise<QuoteRunLaunch> {
  const response = await fetchImpl(`/api/v1/tenants/${encodeURIComponent(tenantId)}/quote-runs`, {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      'X-Ui-Trace-Id': 'brw-s01-local-trace',
    },
    body: JSON.stringify(toLoanPassQuoteIntakePayload(intake)),
  });

  return readPipelineJson<QuoteRunLaunch>(response, {
    endpointContext: 'quote run launch',
    unavailableMessage: 'BFF borrower intake boundary is temporarily unavailable.',
  });
}

export async function fetchPricingWaterfall(
  tenantId: string,
  runId: string,
  fetchImpl: typeof fetch = fetch,
): Promise<PricingWaterfallView> {
  const response = await fetchImpl(`/api/v1/tenants/${encodeURIComponent(tenantId)}/quote-runs/${encodeURIComponent(runId)}/pricing-waterfall`, {
    headers: {
      Accept: 'application/json',
      'X-Ui-Trace-Id': 'pw-s05-local-trace',
    },
  });

  return readPipelineJson<PricingWaterfallView>(response, {
    endpointContext: 'pricing waterfall evidence',
    unavailableMessage: 'Pricing waterfall evidence is temporarily unavailable.',
  });
}

export async function fetchQuoteJourneyMap(
  tenantId: string,
  runId: string,
  fetchImpl: typeof fetch = fetch,
): Promise<QuoteJourneyMapView> {
  const response = await fetchImpl(`/api/v1/tenants/${encodeURIComponent(tenantId)}/quote-runs/${encodeURIComponent(runId)}/journey`, {
    headers: {
      Accept: 'application/json',
      'X-Ui-Trace-Id': 'journey-s19-local-trace',
    },
  });

  return readPipelineJson<QuoteJourneyMapView>(response, {
    endpointContext: 'quote journey map',
    unavailableMessage: 'Quote journey map is temporarily unavailable.',
  });
}

export async function fetchLockWorkflow(
  tenantId: string,
  runId: string,
  selectedOfferId: string | null | undefined,
  fetchImpl: typeof fetch = fetch,
): Promise<LockWorkflowView> {
  const query = selectedOfferId ? `?selectedOfferId=${encodeURIComponent(selectedOfferId)}` : '';
  const response = await fetchImpl(`/api/v1/tenants/${encodeURIComponent(tenantId)}/quote-runs/${encodeURIComponent(runId)}/lock${query}`, {
    headers: {
      Accept: 'application/json',
      'X-Ui-Trace-Id': 'ql-s12-local-trace',
    },
  });

  return readPipelineJson<LockWorkflowView>(response, {
    endpointContext: 'lock workflow evidence',
    unavailableMessage: 'Lock workflow evidence is temporarily unavailable.',
  });
}

export async function confirmLockWorkflowAction(
  tenantId: string,
  runId: string,
  request: LockConfirmationRequest,
  fetchImpl: typeof fetch = fetch,
): Promise<LockConfirmationResult> {
  const response = await fetchImpl(`/api/v1/tenants/${encodeURIComponent(tenantId)}/quote-runs/${encodeURIComponent(runId)}/lock/confirm`, {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      'X-Ui-Trace-Id': 'ql-s12-local-trace',
    },
    body: JSON.stringify(request),
  });

  return readPipelineJson<LockConfirmationResult>(response, {
    endpointContext: 'lock confirmation',
    unavailableMessage: 'Lock confirmation is temporarily unavailable.',
  });
}
