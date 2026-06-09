export type BorrowerIntake = {
  borrowerName: string;
  contactEmail: string;
  quoteGoal: string;
  scenarioName: string;
  channel: string;
  externalLoanId: string;
  sourceSystem: string;
  borrowerCreditStatus: string;
  creditScore: string;
  loanPurpose: string;
  loanAmount: string;
  propertyState: string;
  occupancyType: string;
  monthlyIncome: string;
  liquidAssets: string;
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
};

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
