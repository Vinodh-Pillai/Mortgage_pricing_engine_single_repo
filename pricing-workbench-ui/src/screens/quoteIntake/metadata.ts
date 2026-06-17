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
    fallbackFields: ['borrowerFirstName', 'borrowerLastName', 'numberOfBorrowers', 'contactEmail', 'creditStatus', 'decisionCreditScore', 'citizenshipType', 'professional'],
  },
  {
    id: 3,
    section: 'loan-structure',
    label: 'Loan Structure',
    shortLabel: 'Loan',
    summary: 'Capture loan purpose, structure, financing, term, and lock-period facts.',
    fallbackFields: ['loanPurpose', 'baseLoanAmount', 'downPaymentOrEquity', 'lienPosition', 'desiredLoanTerm', 'desiredAmortizationType', 'desiredRateLockPeriod', 'desiredInterestRate', 'prepaymentPenaltyTerm', 'waiveEscrows', 'interestOnly'],
  },
  {
    id: 4,
    section: 'property',
    label: 'Property',
    shortLabel: 'Property',
    summary: 'Capture location, occupancy, property type, unit, value, and property flags.',
    fallbackFields: ['state', 'propertyZip', 'propertyType', 'occupancyType', 'numberOfUnits', 'purchasePrice', 'appraisedValue', 'propertyLocation', 'numberOfLeasedUnits', 'shortTermRental', 'monthlyMarketRent', 'investorExperience', 'propertySquareFootage', 'propertyAcreageNumber', 'monthlyTaxes', 'monthlyInsurance', 'monthlyHOA', 'annualTaxes', 'annualInsurance', 'annualHOA'],
  },
  {
    id: 5,
    section: 'income-assets',
    label: 'Income & Assets',
    shortLabel: 'Income',
    summary: 'Capture income, debt, reserves, assets, and verification facts.',
    fallbackFields: ['selfEmployed', 'totalBorrowerIncome', 'monthlyDebt', 'estimatedDti', 'monthsOfReserves', 'liquidAssets', 'documentationType', 'secondaryDocumentationType', 'estimatedDscr', 'gift', 'achPayment', 'mortgageLatePayments', 'creditEvent', 'wholesaleCompensation', 'lockExtension', 'lockExtension2', 'concession', 'secondaryAdjustment', 'aus', 'manualUnderwriting', 'additionalMonthlyHousingExpenses', 'additionalAnnualHousingExpenses'],
  },
];

export const borrowerIntakeFields = [
  'channel', 'loanNumber', 'borrowerFirstName', 'borrowerLastName', 'numberOfBorrowers', 'contactEmail', 'creditStatus', 'decisionCreditScore', 'citizenshipType', 'professional', 'loanPurpose', 'baseLoanAmount', 'downPaymentOrEquity', 'lienPosition', 'desiredLoanTerm', 'desiredAmortizationType', 'desiredRateLockPeriod', 'desiredInterestRate', 'prepaymentPenaltyTerm', 'waiveEscrows', 'interestOnly', 'mortgageType', 'state', 'propertyZip', 'propertyType', 'occupancyType', 'numberOfUnits', 'propertyLocation', 'numberOfLeasedUnits', 'shortTermRental', 'monthlyMarketRent', 'investorExperience', 'additionalMonthlyHousingExpenses', 'propertySquareFootage', 'propertyAcreageNumber', 'monthlyTaxes', 'monthlyInsurance', 'monthlyHOA', 'additionalAnnualHousingExpenses', 'annualTaxes', 'annualInsurance', 'annualHOA', 'purchasePrice', 'appraisedValue', 'selfEmployed', 'totalBorrowerIncome', 'monthlyDebt', 'estimatedDti', 'monthsOfReserves', 'liquidAssets', 'documentationType', 'secondaryDocumentationType', 'estimatedDscr', 'gift', 'achPayment', 'mortgageLatePayments', 'creditEvent', 'wholesaleCompensation', 'lockExtension', 'lockExtension2', 'concession', 'secondaryAdjustment', 'aus', 'manualUnderwriting',
] as const satisfies ReadonlyArray<keyof BorrowerIntake>;

const borrowerFieldSet = new Set<string>(borrowerIntakeFields);

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

const selectBackedFallbackFields = new Set<keyof BorrowerIntake>(['channel', 'documentationType', 'secondaryDocumentationType', 'state', 'propertyType', 'occupancyType', 'lienPosition', 'desiredAmortizationType', 'mortgageType', 'selfEmployed', 'citizenshipType', 'propertyLocation', 'investorExperience', 'wholesaleCompensation', 'prepaymentPenaltyTerm', 'waiveEscrows', 'gift', 'aus', 'manualUnderwriting', 'interestOnly', 'achPayment', 'mortgageLatePayments', 'creditEvent', 'concession', 'secondaryAdjustment', 'shortTermRental', 'professional']);

const fieldLabels: Partial<Record<keyof BorrowerIntake, string>> = {
  channel: 'Channel',
  loanNumber: 'Loan number',
  borrowerFirstName: 'Borrower first name',
  borrowerLastName: 'Borrower last name',
  numberOfBorrowers: 'Number of borrowers',
  contactEmail: 'Contact email',
  creditStatus: 'Credit status',
  decisionCreditScore: 'Decision credit score',
  citizenshipType: 'Citizenship type',
  professional: 'Professional',
  loanPurpose: 'Loan purpose',
  baseLoanAmount: 'Base loan amount',
  downPaymentOrEquity: 'Down payment or equity',
  lienPosition: 'Lien position',
  desiredLoanTerm: 'Desired loan term',
  desiredAmortizationType: 'Desired amortization type',
  desiredRateLockPeriod: 'Desired rate lock period',
  desiredInterestRate: 'Desired interest rate',
  prepaymentPenaltyTerm: 'Prepayment penalty term',
  waiveEscrows: 'Waive escrows',
  interestOnly: 'Interest only',
  mortgageType: 'Mortgage type',
  state: 'State',
  propertyZip: 'Property ZIP',
  propertyType: 'Property type',
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
  totalBorrowerIncome: 'Total borrower income',
  monthlyDebt: 'Monthly debt',
  estimatedDti: 'Estimated DTI',
  monthsOfReserves: 'Months of reserves',
  liquidAssets: 'Liquid assets',
  documentationType: 'Documentation type',
  secondaryDocumentationType: 'Secondary documentation type',
  estimatedDscr: 'Estimated DSCR',
  gift: 'Gift',
  achPayment: 'ACH payment',
  mortgageLatePayments: 'Mortgage late payments',
  creditEvent: 'Credit event',
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
    'income-assets': ['incomeassets', 'income', 'assets'],
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
