export type BorrowerIntake = {
  quoteIntent: string;
  channel: string;
  scenarioName: string;
  externalLoanId: string;
  sourceSystem: string;
  borrowerName: string;
  borrowerRole: string;
  coBorrowerName: string;
  coBorrowerRole: string;
  contactEmail: string;
  creditStatus: string;
  creditScore: string;
  creditScoreSource: string;
  creditReportDate: string;
  creditReadiness: string;
  loanPurpose: string;
  loanAmount: string;
  purchasePriceOrValue: string;
  downPaymentOrEquity: string;
  subordinateFinancingAmount: string;
  helocDrawnAmount: string;
  helocLimitAmount: string;
  lienPosition: string;
  termMonths: string;
  amortizationType: string;
  requestedLockPeriodDays: string;
  propertyState: string;
  propertyCounty: string;
  propertyZip: string;
  propertyType: string;
  occupancyType: string;
  unitCount: string;
  purchasePrice: string;
  appraisedValue: string;
  condoProjectType: string;
  manufacturedHomeFlag: string;
  monthlyIncome: string;
  incomeType: string;
  employmentType: string;
  monthlyDebt: string;
  suppliedDti: string;
  reserveMonths: string;
  incomeVerificationStatus: string;
  assetVerificationStatus: string;
  liquidAssets: string;
  reserves: string;
  productFamily: string;
  productPreference: string;
  quoteFilters: string;
  effectiveDate: string;
  actorId: string;
  clientContext: string;
};

export type ScenarioIntakeField = {
  fieldId: keyof BorrowerIntake;
  label: string;
  groupId: string;
  dataType: 'text' | 'email' | 'textarea' | 'number';
  required: boolean;
  helpText: string;
  sourceRef: string;
  decisionQuality: 'VERIFIED' | 'UNKNOWN' | 'CONFLICTING';
  validationMessages: string[];
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

export type LaunchState =
  | { kind: 'idle' }
  | { kind: 'submitting' }
  | { kind: 'blocked'; validation: IntakeValidation }
  | { kind: 'created'; launch: QuoteRunLaunch }
  | { kind: 'outage'; message: string };

export type MetadataState =
  | { kind: 'loading' }
  | { kind: 'loaded'; metadata: ScenarioIntakeMetadata }
  | { kind: 'unreachable'; message: string };

export type RedactedWaterfallValue = {
  value: string | null;
  redacted: boolean;
  reason: string | null;
};

export type WaterfallLedgerRow = {
  ordinal: number;
  step: string;
  inputValue: RedactedWaterfallValue;
  operation: string;
  outputValue: RedactedWaterfallValue;
  configRef: string;
  reasonCode: string;
  roundingMode: string | null;
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
    roundingTraceRefs: string[];
  };
  blockers: Array<{ code: string; message: string; sourceRef: string }>;
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

  if (response.status >= 500) {
    throw new Error('Scenario intake metadata is temporarily unavailable.');
  }

  return (await response.json()) as ScenarioIntakeMetadata;
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
    body: JSON.stringify(intake),
  });

  const launch = (await response.json()) as QuoteRunLaunch;
  if (response.status >= 500) {
    throw new Error('BFF borrower intake boundary is temporarily unavailable.');
  }

  return launch;
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

  if (response.status >= 500) {
    throw new Error('Pricing waterfall evidence is temporarily unavailable.');
  }

  return (await response.json()) as PricingWaterfallView;
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

  if (response.status >= 500) {
    throw new Error('Quote journey map is temporarily unavailable.');
  }

  return (await response.json()) as QuoteJourneyMapView;
}
