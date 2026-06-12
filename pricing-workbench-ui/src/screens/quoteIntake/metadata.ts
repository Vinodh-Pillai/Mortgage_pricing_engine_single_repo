import type { BorrowerIntake, MetadataState, ProgressiveQuickQuoteState, ScenarioIntakeField, ScenarioIntakeFieldGroup, ScenarioIntakeMetadata } from '../../lib/api/quoteRuns';

export type QuoteIntakeStepId = 1 | 2 | 3 | 4 | 5 | 6;
export type QuoteIntakeSection = 'scenario-identity' | 'borrower-credit' | 'loan-structure' | 'property' | 'income-assets' | 'preferences';

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
    label: 'Scenario Identity',
    shortLabel: 'Identity',
    summary: 'Create a draft scenario before collecting downstream quote facts.',
    fallbackFields: ['quoteIntent', 'channel', 'scenarioName', 'externalLoanId'],
  },
  {
    id: 2,
    section: 'borrower-credit',
    label: 'Borrower & Credit',
    shortLabel: 'Borrower',
    summary: 'Capture borrower and credit facts from configured metadata.',
    fallbackFields: ['borrowerName', 'borrowerRole', 'coBorrowerName', 'coBorrowerRole', 'contactEmail', 'creditStatus', 'creditScore', 'creditScoreSource', 'creditReportDate', 'creditReadiness'],
  },
  {
    id: 3,
    section: 'loan-structure',
    label: 'Loan Structure',
    shortLabel: 'Loan',
    summary: 'Capture loan purpose, structure, financing, term, and lock-period facts.',
    fallbackFields: ['loanPurpose', 'loanAmount', 'purchasePriceOrValue', 'downPaymentOrEquity', 'lienPosition', 'termMonths', 'amortizationType', 'subordinateFinancingAmount', 'helocDrawnAmount', 'helocLimitAmount', 'requestedLockPeriodDays'],
  },
  {
    id: 4,
    section: 'property',
    label: 'Property',
    shortLabel: 'Property',
    summary: 'Capture location, occupancy, property type, unit, value, and property flags.',
    fallbackFields: ['propertyState', 'propertyCounty', 'propertyZip', 'propertyType', 'occupancyType', 'unitCount', 'purchasePrice', 'appraisedValue', 'condoProjectType', 'manufacturedHomeFlag'],
  },
  {
    id: 5,
    section: 'income-assets',
    label: 'Income & Assets',
    shortLabel: 'Income',
    summary: 'Capture income, debt, reserves, assets, and verification facts.',
    fallbackFields: ['monthlyIncome', 'incomeType', 'employmentType', 'monthlyDebt', 'suppliedDti', 'reserveMonths', 'incomeVerificationStatus', 'assetVerificationStatus', 'liquidAssets', 'reserves'],
  },
  {
    id: 6,
    section: 'preferences',
    label: 'Preferences & Filters',
    shortLabel: 'Launch',
    summary: 'Confirm preferences and launch the quote run or show setup blockers.',
    fallbackFields: ['productFamily', 'productPreference', 'quoteFilters', 'effectiveDate', 'actorId', 'clientContext'],
  },
];

export const borrowerIntakeFields = [
  'quoteIntent', 'channel', 'scenarioName', 'externalLoanId', 'sourceSystem', 'borrowerName', 'borrowerRole', 'coBorrowerName', 'coBorrowerRole', 'contactEmail', 'creditStatus', 'creditScore', 'creditScoreSource', 'creditReportDate', 'creditReadiness', 'loanPurpose', 'loanAmount', 'purchasePriceOrValue', 'downPaymentOrEquity', 'subordinateFinancingAmount', 'helocDrawnAmount', 'helocLimitAmount', 'lienPosition', 'termMonths', 'amortizationType', 'requestedLockPeriodDays', 'propertyState', 'propertyCounty', 'propertyZip', 'propertyType', 'occupancyType', 'unitCount', 'purchasePrice', 'appraisedValue', 'condoProjectType', 'manufacturedHomeFlag', 'monthlyIncome', 'incomeType', 'employmentType', 'monthlyDebt', 'suppliedDti', 'reserveMonths', 'incomeVerificationStatus', 'assetVerificationStatus', 'liquidAssets', 'reserves', 'productFamily', 'productPreference', 'quoteFilters', 'effectiveDate', 'actorId', 'clientContext',
] as const satisfies ReadonlyArray<keyof BorrowerIntake>;

const borrowerFieldSet = new Set<string>(borrowerIntakeFields);

const requiredFallbackFields = new Set<keyof BorrowerIntake>(['quoteIntent', 'channel', 'borrowerName', 'contactEmail', 'propertyState', 'propertyZip']);

const fieldLabels: Record<keyof BorrowerIntake, string> = {
  quoteIntent: 'Quote intent',
  channel: 'Channel',
  scenarioName: 'Scenario name',
  externalLoanId: 'External loan ID',
  sourceSystem: 'Source system',
  borrowerName: 'Borrower name',
  borrowerRole: 'Borrower role',
  coBorrowerName: 'Co-borrower name',
  coBorrowerRole: 'Co-borrower role',
  contactEmail: 'Contact email',
  creditStatus: 'Credit status',
  creditScore: 'Representative credit score',
  creditScoreSource: 'Credit score source',
  creditReportDate: 'Credit report date',
  creditReadiness: 'Credit readiness notes',
  loanPurpose: 'Loan purpose',
  loanAmount: 'Loan amount',
  purchasePriceOrValue: 'Purchase price or estimated value',
  downPaymentOrEquity: 'Down payment or equity',
  subordinateFinancingAmount: 'Subordinate financing amount',
  helocDrawnAmount: 'HELOC drawn amount',
  helocLimitAmount: 'HELOC limit amount',
  lienPosition: 'Lien position',
  termMonths: 'Term months',
  amortizationType: 'Amortization type',
  requestedLockPeriodDays: 'Requested lock period days',
  propertyState: 'Property state',
  propertyCounty: 'Property county',
  propertyZip: 'Property ZIP',
  propertyType: 'Property type',
  occupancyType: 'Occupancy type',
  unitCount: 'Unit count',
  purchasePrice: 'Purchase price',
  appraisedValue: 'Appraised value',
  condoProjectType: 'Condo project type',
  manufacturedHomeFlag: 'Manufactured home flag',
  monthlyIncome: 'Monthly qualifying income',
  incomeType: 'Income type',
  employmentType: 'Employment type',
  monthlyDebt: 'Monthly debt',
  suppliedDti: 'Supplied DTI',
  reserveMonths: 'Reserve months',
  incomeVerificationStatus: 'Income verification status',
  assetVerificationStatus: 'Asset verification status',
  liquidAssets: 'Liquid assets',
  reserves: 'Reserves',
  productFamily: 'Product family preference',
  productPreference: 'Product preference notes',
  quoteFilters: 'Quote filters',
  effectiveDate: 'Effective date',
  actorId: 'Actor ID',
  clientContext: 'Client context',
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
  if (metadataFields.length > 0) return metadataFields;
  return fallbackFieldsForStep(step);
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
  const isNumeric = /amount|score|months|dti|count|value|price|debt|income|assets|reserves|days/i.test(fieldId);
  const isEmail = fieldId === 'contactEmail';
  const isTextarea = fieldId === 'quoteFilters' || fieldId === 'clientContext' || fieldId === 'productPreference';
  return {
    fieldId,
    label: fieldLabels[fieldId],
    groupId,
    dataType: isTextarea ? 'textarea' : isEmail ? 'email' : isNumeric ? 'number' : 'text',
    required: requiredFallbackFields.has(fieldId),
    helpText: `${fieldLabels[fieldId]} supplied by intake metadata or local fallback descriptor.`,
    sourceRef: 'intake-metadata',
    decisionQuality: 'UNKNOWN',
    validationMessages: [],
  };
}

function sectionAliases(section: QuoteIntakeSection) {
  const aliases: Record<QuoteIntakeSection, string[]> = {
    'scenario-identity': ['scenarioidentity', 'identity', 'scenario', 'basics'],
    'borrower-credit': ['borrowercredit', 'borrower', 'credit'],
    'loan-structure': ['loanstructure', 'loan'],
    property: ['property'],
    'income-assets': ['incomeassets', 'income', 'assets'],
    preferences: ['preferences', 'product', 'filters', 'lock', 'decisionquality'],
  };
  return aliases[section];
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
