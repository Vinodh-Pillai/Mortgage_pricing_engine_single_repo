import type { BorrowerIntake, MetadataState, ProgressiveQuickQuoteState, ScenarioIntakeField, ScenarioIntakeFieldGroup, ScenarioIntakeMetadata } from '../../lib/api/quoteRuns';

export type QuoteIntakeStepId = 1 | 2 | 3 | 4 | 5;
export type QuoteIntakeSection = 'scenario-identity' | 'borrower-credit' | 'loan-structure' | 'property' | 'income-assets';

export type QuoteIntakeStepDefinition = {
  id: QuoteIntakeStepId;
  section: QuoteIntakeSection;
  label: string;
  shortLabel: string;
  summary: string;
  fallbackFields: Array<keyof BorrowerIntake>;
};

export const quoteIntakeTraceId = 'brw-s01-local-trace';

export const quoteIntakeSteps: QuoteIntakeStepDefinition[] = [
  {
    id: 1,
    section: 'scenario-identity',
    label: 'Loan Basics',
    shortLabel: 'Basics',
    summary: 'Capture the minimum loan facts needed to create or store a draft pipeline record.',
    fallbackFields: ['borrowerLastName', 'loanNumber', 'mortgageType', 'channel'],
  },
  {
    id: 2,
    section: 'borrower-credit',
    label: 'Borrower & Credit',
    shortLabel: 'Borrower',
    summary: 'Capture borrower and credit facts from configured metadata.',
    fallbackFields: ['borrowerFirstName', 'borrowerLastName', 'numberOfBorrowers', 'contactEmail', 'decisionCreditScore', 'creditScoreType', 'citizenshipType', 'professional', 'firstTimeHomeBuyer', 'firstTimeInvestor', 'selfEmployed', 'selfEmployedTimeFrame', 'documentationType', 'secondaryDocumentationType', 'documentationTypeTimeFrame', 'totalBorrowerIncome', 'monthlyDebt', 'estimatedDti', 'estimatedDSCR', 'monthsOfReserves'],
  },
  {
    id: 3,
    section: 'loan-structure',
    label: 'Loan Structure',
    shortLabel: 'Loan',
    summary: 'Capture loan purpose, structure, financing, term, and lock-period facts.',
    fallbackFields: ['loanPurpose', 'baseLoanAmount', 'purchasePrice', 'appraisedValue', 'downPayment', 'percentDownpayment', 'loanToValue', 'combinedLoanToValue', 'cashOutAmount', 'loanBalance', 'firstLoanBalance', 'secondLoanBalance', 'firstLienAmount', 'secondLienAmount', 'lienPosition', 'refinancingType', 'desiredLoanTerm', 'desiredAmortizationType', 'desiredRateLockPeriod', 'lockPeriodType', 'desiredInterestRate'],
  },
  {
    id: 4,
    section: 'property',
    label: 'Property',
    shortLabel: 'Property',
    summary: 'Capture location, occupancy, property type, unit, value, and property flags.',
    fallbackFields: ['state', 'zip', 'countyFips', 'countyCode', 'countyName', 'city', 'street', 'addressSearchString', 'propertyType', 'occupancyType', 'numberOfUnits', 'propertyLocation', 'numberOfLeasedUnits', 'shortTermRental', 'monthlyMarketRent', 'propertyRentalIncome', 'rentalIncomeMightBeUsed', 'investorExperience', 'propertySquareFootage', 'propertyAcreageNumber', 'condoApprovalType', 'acquisitionDate', 'monthlyTaxes', 'monthlyInsurance', 'monthlyHOA', 'annualTaxes', 'annualInsurance', 'annualHOA'],
  },
  {
    id: 5,
    section: 'income-assets',
    label: 'Product Controls',
    shortLabel: 'Controls',
    summary: 'Capture LoanPass product controls, housing expense, VA, lock, and credit-event details.',
    fallbackFields: ['mortgageType', 'loanQualificationType', 'prepaymentPenaltyTerm', 'prePaymentPenaltyStructureType', 'waiveEscrows', 'mortgageInsuranceType', 'miOptionType', 'interestOnly', 'gift', 'achPayment', 'wholesaleCompensation', 'compensationPaidType', 'vaLoanType', 'vaFundingFeeExemptionType', 'vaFirstTimeUse', 'additionalMonthlyHousingExpenses', 'additionalAnnualHousingExpenses', 'mortgageLatePayments', 'mortgageHistoryDesc', 'creditEvent', 'chapter7BankruptcyDate', 'chapter11BankruptcyDate', 'chapter13BankruptcyDate', 'deedInLieuDate', 'foreclosureDate', 'shortSaleDate', 'mortgageModificationDate', 'forbearanceDate', 'lockExtension', 'lockExtension2', 'concession', 'secondaryAdjustment', 'aus', 'manualUnderwriting'],
  },
];

export const borrowerIntakeFields = [
  'channel', 'loanNumber', 'borrowerFirstName', 'borrowerLastName', 'numberOfBorrowers', 'contactEmail', 'decisionCreditScore', 'creditScoreType', 'citizenshipType', 'professional', 'firstTimeHomeBuyer', 'firstTimeInvestor', 'loanPurpose', 'baseLoanAmount', 'purchasePrice', 'appraisedValue', 'downPayment', 'percentDownpayment', 'loanToValue', 'combinedLoanToValue', 'cashOutAmount', 'loanBalance', 'firstLoanBalance', 'secondLoanBalance', 'firstLienAmount', 'secondLienAmount', 'lienPosition', 'refinancingType', 'desiredLoanTerm', 'desiredAmortizationType', 'desiredRateLockPeriod', 'lockPeriodType', 'desiredInterestRate', 'prepaymentPenaltyTerm', 'prePaymentPenaltyStructureType', 'waiveEscrows', 'interestOnly', 'mortgageType', 'loanQualificationType', 'mortgageInsuranceType', 'miOptionType', 'gift', 'achPayment', 'wholesaleCompensation', 'compensationPaidType', 'vaLoanType', 'vaFundingFeeExemptionType', 'vaFirstTimeUse', 'state', 'zip', 'countyFips', 'countyCode', 'countyName', 'city', 'street', 'addressSearchString', 'propertyType', 'occupancyType', 'numberOfUnits', 'propertyLocation', 'numberOfLeasedUnits', 'shortTermRental', 'propertySquareFootage', 'propertyAcreageNumber', 'condoApprovalType', 'acquisitionDate', 'monthlyMarketRent', 'propertyRentalIncome', 'rentalIncomeMightBeUsed', 'investorExperience', 'additionalMonthlyHousingExpenses', 'additionalAnnualHousingExpenses', 'monthlyTaxes', 'monthlyInsurance', 'monthlyHOA', 'annualTaxes', 'annualInsurance', 'annualHOA', 'selfEmployed', 'selfEmployedTimeFrame', 'totalBorrowerIncome', 'monthlyDebt', 'totalLiabilityMonthlyPayment', 'estimatedDti', 'estimatedDSCR', 'monthsOfReserves', 'liquidAssets', 'documentationType', 'secondaryDocumentationType', 'documentationTypeTimeFrame', 'mortgageLatePayments', 'mortgageHistoryDesc', 'creditEvent', 'chapter7BankruptcyDate', 'chapter11BankruptcyDate', 'chapter13BankruptcyDate', 'deedInLieuDate', 'foreclosureDate', 'shortSaleDate', 'mortgageModificationDate', 'forbearanceDate', 'lockExtension', 'lockExtension2', 'concession', 'secondaryAdjustment', 'aus', 'manualUnderwriting',
] as const satisfies ReadonlyArray<keyof BorrowerIntake>;

const configDropdownFieldAliases = [
  'investorCode',
  'channelCode',
  'channelType',
  'amortizationType',
  'loanTermType',
  'propertyInformationType',
  'transactionType',
  'incomeDocumentationType',
] as const satisfies ReadonlyArray<keyof BorrowerIntake>;

const borrowerFieldSet = new Set<string>([...borrowerIntakeFields, ...configDropdownFieldAliases]);

const optionalTechnicalFields = new Set<keyof BorrowerIntake>();

const requiredFallbackFields = new Set<keyof BorrowerIntake>(['borrowerLastName', 'loanNumber', 'mortgageType']);

const complexFieldHelpTooltips: Partial<Record<keyof BorrowerIntake, string>> = {
  estimatedDti: 'Debt-to-Income ratio supplied for LoanPass quote intake.',
  baseLoanAmount: 'Base loan amount sent to LoanPass quote intake.',
  desiredRateLockPeriod: 'Desired rate lock period in days.',
  desiredAmortizationType: 'Desired LoanPass amortization type.',
  lienPosition: 'First lien is the primary mortgage. Second lien is a home equity loan or HELOC.',
};

const complexFieldIds = new Set<keyof BorrowerIntake>(Object.keys(complexFieldHelpTooltips) as Array<keyof BorrowerIntake>);

const selectBackedFallbackFields = new Set<keyof BorrowerIntake>(['channel', 'channelCode', 'channelType', 'investorCode', 'documentationType', 'incomeDocumentationType', 'secondaryDocumentationType', 'documentationTypeTimeFrame', 'selfEmployedTimeFrame', 'state', 'propertyType', 'propertyInformationType', 'occupancyType', 'lienPosition', 'desiredAmortizationType', 'amortizationType', 'loanTermType', 'mortgageType', 'loanQualificationType', 'lockPeriodType', 'mortgageInsuranceType', 'miOptionType', 'selfEmployed', 'citizenshipType', 'propertyLocation', 'investorExperience', 'wholesaleCompensation', 'compensationPaidType', 'prepaymentPenaltyTerm', 'prePaymentPenaltyStructureType', 'waiveEscrows', 'gift', 'aus', 'manualUnderwriting', 'interestOnly', 'achPayment', 'mortgageLatePayments', 'creditEvent', 'concession', 'secondaryAdjustment', 'shortTermRental', 'professional', 'firstTimeHomeBuyer', 'firstTimeInvestor', 'rentalIncomeMightBeUsed', 'condoApprovalType', 'vaLoanType', 'vaFundingFeeExemptionType', 'vaFirstTimeUse', 'refinancingType', 'transactionType']);

const fieldLabels: Partial<Record<keyof BorrowerIntake, string>> = {
  channel: 'Channel',
  channelCode: 'Channel code',
  channelType: 'Channel type',
  loanNumber: 'Loan Number',
  borrowerFirstName: 'Borrower first name',
  borrowerLastName: 'Borrower Last Name',
  numberOfBorrowers: 'Number of borrowers',
  contactEmail: 'Contact email',
  decisionCreditScore: 'Decision credit score',
  creditScoreType: 'Credit score type',
  citizenshipType: 'Citizenship type',
  investorCode: 'Investor code',
  professional: 'Professional',
  firstTimeHomeBuyer: 'First-time home buyer',
  firstTimeInvestor: 'First-time investor',
  loanPurpose: 'Loan purpose',
  baseLoanAmount: 'Base loan amount',
  downPayment: 'Down payment',
  percentDownpayment: 'Percent down payment',
  loanToValue: 'Loan to value',
  combinedLoanToValue: 'Combined loan to value',
  cashOutAmount: 'Cash-out amount',
  loanBalance: 'Loan balance',
  firstLoanBalance: 'First loan balance',
  secondLoanBalance: 'Second loan balance',
  firstLienAmount: 'First lien amount',
  secondLienAmount: 'Second lien amount',
  lienPosition: 'Lien position',
  loanTermType: 'Loan term type',
  refinancingType: 'Refinancing type',
  desiredLoanTerm: 'Desired loan term',
  desiredAmortizationType: 'Desired amortization type',
  amortizationType: 'Amortization type',
  desiredRateLockPeriod: 'Desired rate lock period',
  lockPeriodType: 'Lock period type',
  desiredInterestRate: 'Desired interest rate',
  prepaymentPenaltyTerm: 'Prepayment penalty term',
  prePaymentPenaltyStructureType: 'Prepayment penalty structure type',
  waiveEscrows: 'Waive escrows',
  interestOnly: 'Interest only',
  mortgageType: 'Mortgage Type',
  loanQualificationType: 'Loan qualification type',
  mortgageInsuranceType: 'Mortgage insurance type',
  miOptionType: 'MI option type',
  compensationPaidType: 'Compensation paid type',
  vaLoanType: 'VA loan type',
  vaFundingFeeExemptionType: 'VA funding fee exemption type',
  vaFirstTimeUse: 'VA first-time use',
  state: 'State',
  zip: 'Property ZIP',
  countyFips: 'County FIPS',
  countyCode: 'County code',
  countyName: 'County name',
  city: 'City',
  street: 'Street',
  addressSearchString: 'Address search string',
  propertyType: 'Property type',
  propertyInformationType: 'Property information type',
  occupancyType: 'Occupancy type',
  numberOfUnits: 'Number of units',
  propertyLocation: 'Property location',
  numberOfLeasedUnits: 'Number of leased units',
  shortTermRental: 'Short-term rental',
  monthlyMarketRent: 'Monthly market rent',
  investorExperience: 'Investor experience',
  additionalMonthlyHousingExpenses: 'Additional monthly housing expenses',
  propertySquareFootage: 'Property square footage',
  propertyAcreageNumber: 'Property acreage number',
  condoApprovalType: 'Condo approval type',
  acquisitionDate: 'Acquisition date',
  propertyRentalIncome: 'Property rental income',
  rentalIncomeMightBeUsed: 'Rental income might be used',
  monthlyTaxes: 'Monthly taxes',
  monthlyInsurance: 'Monthly insurance',
  monthlyHOA: 'Monthly HOA',
  additionalAnnualHousingExpenses: 'Additional annual housing expenses',
  annualTaxes: 'Annual taxes',
  annualInsurance: 'Annual insurance',
  annualHOA: 'Annual HOA',
  purchasePrice: 'Purchase price',
  appraisedValue: 'Appraised value',
  selfEmployed: 'Self-employed',
  selfEmployedTimeFrame: 'Self-employed time frame',
  totalBorrowerIncome: 'Total borrower income',
  monthlyDebt: 'Monthly debt',
  totalLiabilityMonthlyPayment: 'Total liability monthly payment',
  estimatedDti: 'Estimated DTI',
  monthsOfReserves: 'Months of reserves',
  liquidAssets: 'Liquid assets',
  documentationType: 'Documentation type',
  incomeDocumentationType: 'Income documentation type',
  secondaryDocumentationType: 'Secondary documentation type',
  documentationTypeTimeFrame: 'Documentation type time frame',
  estimatedDSCR: 'Estimated DSCR',
  transactionType: 'Transaction type',
  gift: 'Gift',
  achPayment: 'ACH payment',
  mortgageLatePayments: 'Mortgage late payments',
  mortgageHistoryDesc: 'Mortgage history description',
  creditEvent: 'Credit event',
  chapter7BankruptcyDate: 'Chapter 7 bankruptcy date',
  chapter11BankruptcyDate: 'Chapter 11 bankruptcy date',
  chapter13BankruptcyDate: 'Chapter 13 bankruptcy date',
  deedInLieuDate: 'Deed in lieu date',
  foreclosureDate: 'Foreclosure date',
  shortSaleDate: 'Short sale date',
  mortgageModificationDate: 'Mortgage modification date',
  forbearanceDate: 'Forbearance date',
  wholesaleCompensation: 'Wholesale compensation',
  lockExtension: 'Lock extension',
  lockExtension2: 'Lock extension 2',
  concession: 'Concession',
  secondaryAdjustment: 'Secondary adjustment',
  aus: 'AUS',
  manualUnderwriting: 'Manual underwriting',
};

export function isBorrowerIntakeField(value: string): value is keyof BorrowerIntake {
  return borrowerFieldSet.has(value);
}

export async function fetchIntakeMetadata(tenantId: string, fetchImpl: typeof fetch = fetch): Promise<ScenarioIntakeMetadata> {
  const response = await fetchImpl(`/api/v1/tenants/${encodeURIComponent(tenantId)}/quote-runs/intake-metadata`, {
    headers: {
      Accept: 'application/json',
      'X-Ui-Trace-Id': quoteIntakeTraceId,
    },
  });

  if (response.status >= 500) throw new Error('Scenario intake metadata is temporarily unavailable.');
  return (await response.json()) as ScenarioIntakeMetadata;
}

export function fieldsForStep(metadata: ScenarioIntakeMetadata | null | undefined, stepId: QuoteIntakeStepId): ScenarioIntakeField[] {
  const step = quoteIntakeSteps.find((candidate) => candidate.id === stepId) ?? quoteIntakeSteps[0];
  const metadataFields = metadata ? metadataFieldsForStep(metadata, step) : [];
  if (metadataFields.length > 0) return withSectionFallbackComplements(normalizeOptionalTechnicalFields(metadataFields), step).map(normalizeFieldHelpDisplay);
  return fallbackFieldsForStep(step);
}

export function stepsForMetadata(metadata: ScenarioIntakeMetadata | null | undefined): QuoteIntakeStepDefinition[] {
  const configuredOrder = metadata?.quickQuoteState?.progressiveSectionOrder ?? [];
  const orderedSections = configuredOrder.filter((section): section is QuoteIntakeSection => quoteIntakeSteps.some((step) => step.section === section));
  if (orderedSections.length === 0) return quoteIntakeSteps;

  const orderedSteps = orderedSections
    .map((section) => quoteIntakeSteps.find((step) => step.section === section))
    .filter((step): step is QuoteIntakeStepDefinition => Boolean(step));

  const includesFirstStep = orderedSteps.some((step) => step.id === 1);
  return uniqueSteps([
    ...(includesFirstStep ? [] : [quoteIntakeSteps[0]]),
    ...orderedSteps,
  ]);
}

export function metadataFieldsForStep(metadata: ScenarioIntakeMetadata, step: QuoteIntakeStepDefinition): ScenarioIntakeField[] {
  const aliases = sectionAliases(step.section);
  const firstStepFields = new Set((step.id === 1 ? metadata.quickQuoteState?.minimalFirstStepFields ?? [] : []).filter(isBorrowerIntakeField));
  const explicitStepFields = step.id === 1 && firstStepFields.size > 0
    ? metadata.fieldGroups.flatMap((group) => group.fields).filter((field) => firstStepFields.has(field.fieldId))
    : [];
  const groupedFields = metadata.fieldGroups
    .filter((group) => aliases.some((alias) => normalized(group.groupId).includes(alias) || normalized(group.label).includes(alias)))
    .flatMap((group) => group.fields)
    .filter((field) => isBorrowerIntakeField(field.fieldId));

  return uniqueFields([...explicitStepFields, ...groupedFields]);
}

export function fallbackFieldsForStep(step: QuoteIntakeStepDefinition): ScenarioIntakeField[] {
  return step.fallbackFields.map((fieldId) => fallbackField(fieldId, step.section));
}

export function normalizeMetadataState(state: MetadataState): { metadata: ScenarioIntakeMetadata | null; visualState: 'loading' | 'ready' | 'needs-attention' } {
  if (state.kind === 'loading') return { metadata: null, visualState: 'loading' };
  if (state.kind === 'unreachable') return { metadata: fallbackMetadata(state.message), visualState: 'needs-attention' };
  return { metadata: state.metadata, visualState: state.metadata.validationIssues.some((issue) => issue.severity === 'BLOCKING') ? 'needs-attention' : 'ready' };
}

export function fallbackMetadata(reason = 'Intake metadata unavailable; local fallback field descriptors are used until connected metadata is restored.'): ScenarioIntakeMetadata {
  const groups: ScenarioIntakeFieldGroup[] = quoteIntakeSteps.map((step) => ({
    groupId: step.section,
    label: step.label,
    helpText: step.summary,
    fields: fallbackFieldsForStep(step),
  }));
  return {
    tenantContext: 'ui-preview-tenant',
    dependencyStatus: 'FALLBACK_STATIC_DEPENDENCIES_UNAVAILABLE',
    fieldGroups: groups,
    decisionControls: ['Use configured metadata when available; fallback descriptors only preserve local intake usability.'],
    validationIssues: [{ code: 'INTAKE_METADATA_UNAVAILABLE', fieldPath: 'metadata', severity: 'WARNING', message: reason }],
    auditPackageId: 'review-package-required-after-scenario-create',
    replayHashRef: 'review-reference-required-after-scenario-create',
    fallbackReason: reason,
    uiTraceId: quoteIntakeTraceId,
    quickQuoteState: fallbackQuickQuoteState(reason),
  };
}

function fallbackQuickQuoteState(reason: string): ProgressiveQuickQuoteState {
  return {
    minimalFirstStepFields: quoteIntakeSteps[0].fallbackFields,
    progressiveSectionOrder: quoteIntakeSteps.map((step) => step.section),
    quoteServiceRequiredFacts: ['scenarioId', 'scenarioVersion'],
    backendOwnedFactSources: ['scenario-service draft', 'quote-service launch'],
    blockedByContracts: [],
    fallbackReason: reason,
  };
}

function fallbackField(fieldId: keyof BorrowerIntake, groupId: string): ScenarioIntakeField {
  const isNumeric = !selectBackedFallbackFields.has(fieldId) && /amount|score|months|dti|count|value|price|debt|income|assets|reserves|days|units|rent|expenses|taxes|insurance|hoa|acreage|square|term|rate|extension|borrowers|dscr/i.test(fieldId);
  const isEmail = fieldId === 'contactEmail';
  const isTextarea = false;
  return {
    fieldId,
    label: fieldLabels[fieldId] ?? String(fieldId).replace(/[A-Z]/g, ' $&').replace(/^./, (character) => character.toUpperCase()),
    groupId,
    dataType: isTextarea ? 'textarea' : isEmail ? 'email' : isNumeric ? 'number' : 'text',
    required: requiredFallbackFields.has(fieldId) && !optionalTechnicalFields.has(fieldId),
    helpText: '',
    helpTooltip: complexFieldHelpTooltips[fieldId],
    showHelpIcon: complexFieldIds.has(fieldId),
    sourceRef: 'intake-metadata',
    decisionQuality: 'UNKNOWN',
    validationMessages: [],
  };
}

function normalizeFieldHelpDisplay(field: ScenarioIntakeField): ScenarioIntakeField {
  const fallbackTooltip = complexFieldHelpTooltips[field.fieldId];
  const showHelpIcon = complexFieldIds.has(field.fieldId);
  return {
    ...field,
    label: fieldLabels[field.fieldId] ?? field.label,
    helpText: '',
    helpTooltip: showHelpIcon ? field.helpTooltip || field.helpText || fallbackTooltip : undefined,
    showHelpIcon,
  };
}

function sectionAliases(section: QuoteIntakeSection) {
  const aliases: Record<QuoteIntakeSection, string[]> = {
    'scenario-identity': ['scenarioidentity', 'identity', 'scenario', 'basics', 'loanbasics'],
    'borrower-credit': ['borrowercredit', 'borrower', 'credit'],
    'loan-structure': ['loanstructure', 'loan'],
    property: ['property'],
    'income-assets': ['incomeassets', 'income', 'assets', 'borrowercreditincome', 'housingexpenses', 'loanproductcontrols', 'productcontrols'],
  };
  return aliases[section];
}

function normalizeOptionalTechnicalFields(fields: ScenarioIntakeField[]) {
  return fields.map((field) => optionalTechnicalFields.has(field.fieldId) ? { ...field, required: false } : field);
}

function withSectionFallbackComplements(fields: ScenarioIntakeField[], step: QuoteIntakeStepDefinition) {
  const scopedFields = fields.filter((field) => !optionalTechnicalFields.has(field.fieldId));
  const seen = new Set(scopedFields.map((field) => field.fieldId));
  const complements = step.fallbackFields.filter((fieldId) => !seen.has(fieldId)).map((fieldId) => fallbackField(fieldId, step.section));
  return uniqueFields([...scopedFields, ...complements]);
}

function normalized(value: string) {
  return value.toLowerCase().replace(/[^a-z0-9]/g, '');
}

function uniqueFields(fields: ScenarioIntakeField[]) {
  const seen = new Set<string>();
  return fields.filter((field) => {
    if (seen.has(field.fieldId)) return false;
    seen.add(field.fieldId);
    return true;
  });
}

function uniqueSteps(steps: QuoteIntakeStepDefinition[]) {
  const seen = new Set<QuoteIntakeStepId>();
  return steps.filter((step) => {
    if (seen.has(step.id)) return false;
    seen.add(step.id);
    return true;
  });
}
