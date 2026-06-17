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
  helpTooltip?: string;
  showHelpIcon?: boolean;
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
    body: JSON.stringify(intake),
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
