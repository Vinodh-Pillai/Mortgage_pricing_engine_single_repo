export type ScenarioAnalysisDimension = {
  dimensionId: string;
  label: string;
  value: string;
  sourceRef: string;
  requiredFacts: string[];
  backendOnly: boolean;
};

export type ScenarioAnalysisBlocker = {
  blockerCode: string;
  severity: string;
  reason: string;
  requiredFacts: string[];
  sourceRef: string;
};

export type ScenarioAnalysisVariant = {
  variantId: string;
  label: string;
  status: string;
  dimensionRefs: string[];
  factRefs: string[];
  guardrailBlockers: ScenarioAnalysisBlocker[];
  resultRefs: string[];
};

export type ScenarioAnalysisBatchRow = {
  rowId: string;
  variantId: string;
  dimensionSummary: string;
  status: string;
  backendResultRef: string;
  guardrailSummary: string;
};

export type ScenarioAnalysisSavedAnalysis = {
  analysisId: string;
  name: string;
  versionRef: string;
  savedAt: string;
  exportRef: string;
  replayHash: string;
};

export type ScenarioAnalysisWorkspaceView = {
  tenantContext: string;
  runId: string;
  dependencyStatus: string;
  dimensions: ScenarioAnalysisDimension[];
  variants: ScenarioAnalysisVariant[];
  batchGrid: ScenarioAnalysisBatchRow[];
  savedAnalyses: ScenarioAnalysisSavedAnalysis[];
  exportRefs: string[];
  replayRefs: string[];
  blockers: ScenarioAnalysisBlocker[];
  fallbackReason: string;
  uiTraceId: string;
  events: string[];
};

export type FicoEligibilityStatus = 'ELIGIBLE' | 'INELIGIBLE' | 'CONDITIONAL' | string;

export type FicoSensitivityEligibilityCell = {
  cellId: string;
  productLabel: string;
  channelLabel: string;
  eligibility: FicoEligibilityStatus;
  blockerReason?: string | null;
  sourceRef?: string | null;
};

export type FicoSensitivityBand = {
  bandId: string;
  label: string;
  minScore?: number | null;
  maxScore?: number | null;
  midpoint?: number | null;
  noteRate?: string | number | null;
  finalPriceBps?: string | number | null;
  payment?: string | number | null;
  apr?: string | number | null;
  eligibility: FicoEligibilityStatus;
  blockerReason?: string | null;
  pricingUnavailableReason?: string | null;
  productLabel?: string | null;
  channelLabel?: string | null;
  sourceRef?: string | null;
  evidenceRefs?: string[];
  currentBorrowerBand?: boolean;
  eligibilityCells?: FicoSensitivityEligibilityCell[];
};

export type FicoSensitivityMetadata = {
  ficoBands?: string[];
  currentFicoSourceRef?: string;
  scoreBandSourceRef?: string;
  pricingSourceRef?: string;
  eligibilitySourceRef?: string;
  exportRef?: string;
};

export type FicoSensitivityView = {
  tenantContext: string;
  runId: string;
  dependencyStatus: string;
  currentFico?: number | null;
  currentFicoLabel?: string | null;
  currentFicoSourceRef?: string | null;
  bands: FicoSensitivityBand[];
  metadata?: FicoSensitivityMetadata;
  exportRefs?: string[];
  replayRefs?: string[];
  auditRefs?: string[];
  blockers?: ScenarioAnalysisBlocker[];
  fallbackReason?: string;
  uiTraceId: string;
  events?: string[];
};

export type LtvEligibilityStatus = 'ELIGIBLE' | 'INELIGIBLE' | 'CONDITIONAL' | string;

export type LtvSensitivityEligibilityCell = {
  cellId: string;
  productLabel: string;
  channelLabel: string;
  eligibility: LtvEligibilityStatus;
  blockerReason?: string | null;
  sourceRef?: string | null;
};

export type LtvSensitivityBand = {
  bandId: string;
  label: string;
  minLtv?: number | null;
  maxLtv?: number | null;
  midpointLtv?: number | null;
  downPaymentPct?: string | number | null;
  noteRate?: string | number | null;
  finalPriceBps?: string | number | null;
  payment?: string | number | null;
  miPremium?: string | number | null;
  miType?: string | null;
  apr?: string | number | null;
  cltv?: string | number | null;
  hcltv?: string | number | null;
  eligibility: LtvEligibilityStatus;
  blockerReason?: string | null;
  pricingUnavailableReason?: string | null;
  productLabel?: string | null;
  channelLabel?: string | null;
  sourceRef?: string | null;
  evidenceRefs?: string[];
  currentBorrowerBand?: boolean;
  eligibilityCells?: LtvSensitivityEligibilityCell[];
};

export type LtvSensitivityMetadata = {
  ltvBands?: string[];
  currentLtvSourceRef?: string;
  currentDownPaymentSourceRef?: string;
  ltvBandSourceRef?: string;
  pricingSourceRef?: string;
  eligibilitySourceRef?: string;
  miSourceRef?: string;
  exportRef?: string;
};

export type LtvSensitivityView = {
  tenantContext: string;
  runId: string;
  dependencyStatus: string;
  currentLtv?: number | null;
  currentLtvLabel?: string | null;
  currentDownPaymentPct?: string | number | null;
  currentDownPaymentLabel?: string | null;
  currentLtvSourceRef?: string | null;
  currentDownPaymentSourceRef?: string | null;
  bands: LtvSensitivityBand[];
  metadata?: LtvSensitivityMetadata;
  exportRefs?: string[];
  replayRefs?: string[];
  auditRefs?: string[];
  blockers?: ScenarioAnalysisBlocker[];
  fallbackReason?: string;
  uiTraceId: string;
  events?: string[];
};

export type ProductComparisonEligibilityStatus = 'ELIGIBLE' | 'INELIGIBLE' | 'CONDITIONAL' | string;

export type ProductComparisonFeatureValue = boolean | string | null;

export type ProductComparisonFeature = {
  featureId: string;
  label: string;
  valuesByProductId: Record<string, ProductComparisonFeatureValue>;
  sourceRef?: string | null;
};

export type ProductComparisonTotalCostPeriod = {
  periodId: string;
  label: string;
  totalCost?: string | number | null;
  principal?: string | number | null;
  interest?: string | number | null;
  mortgageInsurance?: string | number | null;
  fees?: string | number | null;
  sourceRef?: string | null;
  unavailableReason?: string | null;
};

export type ProductComparisonProduct = {
  productId: string;
  label: string;
  productType?: string | null;
  investor?: string | null;
  channel?: string | null;
  noteRate?: string | number | null;
  finalPriceBps?: string | number | null;
  payment?: string | number | null;
  apr?: string | number | null;
  miType?: string | null;
  miPremium?: string | number | null;
  fees?: string | number | null;
  term?: string | number | null;
  lockPeriod?: string | number | null;
  eligibility: ProductComparisonEligibilityStatus;
  eligibilityReason?: string | null;
  currentBestOffer?: boolean;
  pricingUnavailableReason?: string | null;
  sourceRef?: string | null;
  evidenceRefs?: string[];
  totalCostPeriods?: ProductComparisonTotalCostPeriod[];
};

export type ProductComparisonMetadata = {
  productSourceRef?: string;
  pricingSourceRef?: string;
  eligibilitySourceRef?: string;
  totalCostSourceRef?: string;
  featureSourceRef?: string;
  exportRef?: string;
};

export type ProductComparisonView = {
  tenantContext: string;
  runId: string;
  dependencyStatus: string;
  products: ProductComparisonProduct[];
  features?: ProductComparisonFeature[];
  metadata?: ProductComparisonMetadata;
  exportRefs?: string[];
  replayRefs?: string[];
  auditRefs?: string[];
  blockers?: ScenarioAnalysisBlocker[];
  fallbackReason?: string;
  uiTraceId: string;
  events?: string[];
};

export type LockPeriodFloatDownStatus = 'ELIGIBLE' | 'INELIGIBLE' | 'CONDITIONAL' | string;

export type LockPeriodComparisonPeriod = {
  periodId: string;
  label: string;
  days?: string | number | null;
  noteRate?: string | number | null;
  finalPriceBps?: string | number | null;
  payment?: string | number | null;
  extensionCostBpsPerDay?: string | number | null;
  maxExtensionDays?: string | number | null;
  investorPolicyRef?: string | null;
  totalExtensionCost?: string | number | null;
  floatDownEligible: LockPeriodFloatDownStatus;
  floatDownReason?: string | null;
  floatDownCostBps?: string | number | null;
  oneTimeLimit?: string | boolean | null;
  currentLockPeriod?: boolean;
  pricingUnavailableReason?: string | null;
  extensionUnavailableReason?: string | null;
  floatDownUnavailableReason?: string | null;
  sourceRef?: string | null;
  evidenceRefs?: string[];
};

export type LockPeriodComparisonMetadata = {
  lockPeriodSourceRef?: string;
  pricingSourceRef?: string;
  extensionSourceRef?: string;
  floatDownSourceRef?: string;
  exportRef?: string;
};

export type LockPeriodComparisonView = {
  tenantContext: string;
  runId: string;
  dependencyStatus: string;
  currentLockPeriod?: string | number | null;
  currentLockPeriodSourceRef?: string | null;
  periods: LockPeriodComparisonPeriod[];
  metadata?: LockPeriodComparisonMetadata;
  exportRefs?: string[];
  replayRefs?: string[];
  auditRefs?: string[];
  blockers?: ScenarioAnalysisBlocker[];
  fallbackReason?: string;
  uiTraceId: string;
  events?: string[];
};

export type ScenarioRecalculationRequest = {
  changedDimensionId: string;
  requestedValue: string;
  variantFacts: string[];
};

export type ScenarioRecalculationResult = {
  status: string;
  message: string;
  backendResultRefs: string[];
  blockers: ScenarioAnalysisBlocker[];
  events: string[];
  uiTraceId: string;
};

const scenarioAnalysisHeaders = {
  Accept: 'application/json',
  'Content-Type': 'application/json',
  'X-Ui-Trace-Id': 'sa-s15-local-trace',
  'X-Tenant-Context': 'ui-preview-tenant',
};

export async function fetchScenarioAnalysisWorkspace(
  tenantId: string,
  runId: string,
  fetchImpl: typeof fetch = fetch,
): Promise<ScenarioAnalysisWorkspaceView> {
  return fetchScenarioAnalysisJson(
    `/api/v1/tenants/${encodeURIComponent(tenantId)}/quote-runs/${encodeURIComponent(runId)}/what-if/workspace`,
    syntheticScenarioAnalysisWorkspace(tenantId, runId),
    fetchImpl,
  );
}

export async function fetchFicoSensitivity(
  tenantId: string,
  runId: string,
  fetchImpl: typeof fetch = fetch,
): Promise<FicoSensitivityView> {
  return fetchScenarioAnalysisJson(
    `/api/v1/tenants/${encodeURIComponent(tenantId)}/quote-runs/${encodeURIComponent(runId)}/what-if/fico-sensitivity`,
    syntheticFicoSensitivity(tenantId, runId),
    fetchImpl,
  );
}

export async function fetchLtvSensitivity(
  tenantId: string,
  runId: string,
  fetchImpl: typeof fetch = fetch,
): Promise<LtvSensitivityView> {
  return fetchScenarioAnalysisJson(
    `/api/v1/tenants/${encodeURIComponent(tenantId)}/quote-runs/${encodeURIComponent(runId)}/what-if/ltv-sensitivity`,
    syntheticLtvSensitivity(tenantId, runId),
    fetchImpl,
  );
}

export async function fetchProductComparison(
  tenantId: string,
  runId: string,
  fetchImpl: typeof fetch = fetch,
): Promise<ProductComparisonView> {
  return fetchScenarioAnalysisJson(
    `/api/v1/tenants/${encodeURIComponent(tenantId)}/quote-runs/${encodeURIComponent(runId)}/what-if/product-comparison`,
    syntheticProductComparison(tenantId, runId),
    fetchImpl,
  );
}

export async function fetchLockPeriodComparison(
  tenantId: string,
  runId: string,
  fetchImpl: typeof fetch = fetch,
): Promise<LockPeriodComparisonView> {
  return fetchScenarioAnalysisJson(
    `/api/v1/tenants/${encodeURIComponent(tenantId)}/quote-runs/${encodeURIComponent(runId)}/what-if/lock-period-comparison`,
    syntheticLockPeriodComparison(tenantId, runId),
    fetchImpl,
  );
}

export async function recalculateScenarioAnalysis(
  tenantId: string,
  runId: string,
  request: ScenarioRecalculationRequest,
  fetchImpl: typeof fetch = fetch,
): Promise<ScenarioRecalculationResult> {
  try {
    const response = await fetchImpl(`/api/v1/tenants/${encodeURIComponent(tenantId)}/quote-runs/${encodeURIComponent(runId)}/what-if/recalculate`, {
      method: 'POST',
      headers: scenarioAnalysisHeaders,
      body: JSON.stringify(request),
    });
    const ok = response.ok ?? (response.status >= 200 && response.status < 300);
    if (!ok) throw new Error(`HTTP ${response.status}`);
    return (await response.json()) as ScenarioRecalculationResult;
  } catch {
    return syntheticScenarioRecalculationResult(request);
  }
}

async function fetchScenarioAnalysisJson<T>(path: string, fallback: T, fetchImpl: typeof fetch): Promise<T> {
  try {
    const response = await fetchImpl(path, { headers: scenarioAnalysisHeaders });
    const ok = response.ok ?? (response.status >= 200 && response.status < 300);
    if (!ok) throw new Error(`HTTP ${response.status}`);
    return (await response.json()) as T;
  } catch {
    return fallback;
  }
}

function syntheticBlocker(sourceRef: string, reason: string): ScenarioAnalysisBlocker {
  return {
    blockerCode: 'PRODUCTION_INTEGRATION_REQUIRED',
    severity: 'blocked',
    reason,
    requiredFacts: ['scenario-analysis-service response', 'pricing result refs', 'eligibility guardrail refs'],
    sourceRef,
  };
}

function syntheticScenarioAnalysisWorkspace(tenantContext: string, runId: string): ScenarioAnalysisWorkspaceView {
  const blocker = syntheticBlocker('scenario-analysis-service:what-if-workspace', 'Backend scenario-analysis APIs are absent in this local harness run; the UI stages actions without calculating pricing, eligibility, or policy outcomes.');
  return {
    tenantContext,
    runId,
    dependencyStatus: 'local synthetic/dev fixture; production scenario-analysis-service integration required',
    dimensions: [
      { dimensionId: 'fico-source-ref', label: 'FICO source reference', value: 'Backend value unavailable', sourceRef: 'scenario:facts:credit-score', requiredFacts: ['credit score source ref'], backendOnly: true },
      { dimensionId: 'ltv-source-ref', label: 'LTV source reference', value: 'Backend value unavailable', sourceRef: 'scenario:facts:ltv', requiredFacts: ['loan amount ref', 'property value ref'], backendOnly: true },
      { dimensionId: 'lock-period-source-ref', label: 'Lock period source reference', value: 'Backend value unavailable', sourceRef: 'quote:selected-offer:lock-period', requiredFacts: ['selected offer ref'], backendOnly: true },
    ],
    variants: [
      { variantId: 'variant-current-backend-ref', label: 'Current backend scenario ref', status: 'VISIBLE_WITH_BLOCKERS', dimensionRefs: ['fico-source-ref', 'ltv-source-ref'], factRefs: ['scenario:facts:current'], guardrailBlockers: [blocker], resultRefs: [] },
      { variantId: 'variant-what-if-draft-ref', label: 'Local what-if draft ref', status: 'LOCAL_DRAFT_BLOCKED', dimensionRefs: ['lock-period-source-ref'], factRefs: ['scenario:facts:what-if-draft'], guardrailBlockers: [blocker], resultRefs: [] },
    ],
    batchGrid: [
      { rowId: 'row-current-backend-ref', variantId: 'variant-current-backend-ref', dimensionSummary: 'Backend-owned current scenario facts', status: 'blocked', backendResultRef: 'not supplied locally', guardrailSummary: blocker.reason },
      { rowId: 'row-local-draft-ref', variantId: 'variant-what-if-draft-ref', dimensionSummary: 'Local what-if draft facts staged for backend recalculation', status: 'blocked', backendResultRef: 'not supplied locally', guardrailSummary: blocker.reason },
    ],
    savedAnalyses: [
      { analysisId: 'analysis-local-preview', name: 'Local preview analysis', versionRef: 'version-ref-required', savedAt: 'not persisted locally', exportRef: 'export-ref-required', replayHash: 'replay-hash-required' },
    ],
    exportRefs: ['scenario-analysis-export-ref-required'],
    replayRefs: ['scenario-analysis-replay-ref-required'],
    blockers: [blocker],
    fallbackReason: 'Local synthetic/dev fixture is active because scenario-analysis-service is unavailable. Production integrations must supply all calculated values, exports, and replay refs.',
    uiTraceId: 'sa-s15-local-synthetic-trace',
    events: ['scenario-analysis.synthetic.fixture.loaded'],
  };
}

function syntheticScenarioRecalculationResult(request: ScenarioRecalculationRequest): ScenarioRecalculationResult {
  const blocker = syntheticBlocker('scenario-analysis-service:recalculate', 'Recalculation request was staged locally only; backend scenario-analysis-service must calculate and return result refs.');
  return {
    status: 'BLOCKED_PENDING_BACKEND',
    message: `Local what-if request staged for ${request.changedDimensionId}; no mortgage pricing or eligibility calculation was performed in the browser.`,
    backendResultRefs: [],
    blockers: [blocker],
    events: ['scenario-analysis.synthetic.recalculation.staged'],
    uiTraceId: 'sa-s15-local-synthetic-recalculate',
  };
}

function syntheticFicoSensitivity(tenantContext: string, runId: string): FicoSensitivityView {
  const blocker = syntheticBlocker('scenario-analysis-service:fico-sensitivity', 'FICO sensitivity requires backend pricing and eligibility refs.');
  return { tenantContext, runId, dependencyStatus: 'local synthetic/dev fixture; backend FICO sensitivity required', currentFico: null, currentFicoLabel: 'Backend value unavailable', bands: [{ bandId: 'fico-backend-required', label: 'Backend FICO band required', eligibility: 'CONDITIONAL', blockerReason: blocker.reason, pricingUnavailableReason: blocker.reason, evidenceRefs: ['fico-sensitivity-backend-ref-required'], currentBorrowerBand: true }], blockers: [blocker], fallbackReason: blocker.reason, uiTraceId: 'sa-s30-local-synthetic-trace', events: ['fico-sensitivity.synthetic.fixture.loaded'] };
}

function syntheticLtvSensitivity(tenantContext: string, runId: string): LtvSensitivityView {
  const blocker = syntheticBlocker('scenario-analysis-service:ltv-sensitivity', 'LTV sensitivity requires backend pricing, MI, and eligibility refs.');
  return { tenantContext, runId, dependencyStatus: 'local synthetic/dev fixture; backend LTV sensitivity required', currentLtv: null, currentLtvLabel: 'Backend value unavailable', bands: [{ bandId: 'ltv-backend-required', label: 'Backend LTV band required', eligibility: 'CONDITIONAL', blockerReason: blocker.reason, pricingUnavailableReason: blocker.reason, evidenceRefs: ['ltv-sensitivity-backend-ref-required'], currentBorrowerBand: true }], blockers: [blocker], fallbackReason: blocker.reason, uiTraceId: 'sa-s31-local-synthetic-trace', events: ['ltv-sensitivity.synthetic.fixture.loaded'] };
}

function syntheticProductComparison(tenantContext: string, runId: string): ProductComparisonView {
  const blocker = syntheticBlocker('scenario-analysis-service:product-comparison', 'Product comparison requires backend product, pricing, eligibility, and total-cost refs.');
  return {
    tenantContext,
    runId,
    dependencyStatus: 'local synthetic/dev fixture; backend product comparison required',
    products: [
      { productId: 'current-offer-backend-ref', label: 'Current offer backend ref', eligibility: 'CONDITIONAL', currentBestOffer: true, pricingUnavailableReason: blocker.reason, eligibilityReason: blocker.reason, sourceRef: 'quote:selected-offer', evidenceRefs: ['product-comparison-current-offer-ref-required'] },
      { productId: 'alternate-offer-backend-ref', label: 'Alternate offer backend ref', eligibility: 'CONDITIONAL', pricingUnavailableReason: blocker.reason, eligibilityReason: blocker.reason, sourceRef: 'quote:alternate-offer', evidenceRefs: ['product-comparison-alternate-offer-ref-required'] },
    ],
    features: [{ featureId: 'backend-feature-refs', label: 'Backend feature refs required', valuesByProductId: { 'current-offer-backend-ref': 'required', 'alternate-offer-backend-ref': 'required' }, sourceRef: 'scenario-analysis-service:features' }],
    metadata: { productSourceRef: 'product-ref-required', pricingSourceRef: 'pricing-ref-required', eligibilitySourceRef: 'eligibility-ref-required', totalCostSourceRef: 'total-cost-ref-required', featureSourceRef: 'feature-ref-required', exportRef: 'export-ref-required' },
    exportRefs: ['product-comparison-export-ref-required'],
    replayRefs: ['product-comparison-replay-ref-required'],
    auditRefs: ['product-comparison-audit-ref-required'],
    blockers: [blocker],
    fallbackReason: blocker.reason,
    uiTraceId: 'sa-s32-local-synthetic-trace',
    events: ['product-comparison.synthetic.fixture.loaded'],
  };
}

function syntheticLockPeriodComparison(tenantContext: string, runId: string): LockPeriodComparisonView {
  const blocker = syntheticBlocker('scenario-analysis-service:lock-period-comparison', 'Lock period comparison requires backend lock desk policy, pricing, extension, and float-down refs.');
  return {
    tenantContext,
    runId,
    dependencyStatus: 'local synthetic/dev fixture; backend lock-period comparison required',
    currentLockPeriod: 'backend value unavailable',
    currentLockPeriodSourceRef: 'quote:selected-offer:lock-period',
    periods: [
      { periodId: 'current-lock-period-backend-ref', label: 'Current backend lock period ref', days: null, floatDownEligible: 'CONDITIONAL', currentLockPeriod: true, pricingUnavailableReason: blocker.reason, extensionUnavailableReason: blocker.reason, floatDownUnavailableReason: blocker.reason, sourceRef: 'lock-period-current-ref-required', evidenceRefs: ['lock-period-current-ref-required'] },
      { periodId: 'alternate-lock-period-backend-ref', label: 'Alternate backend lock period ref', days: null, floatDownEligible: 'CONDITIONAL', pricingUnavailableReason: blocker.reason, extensionUnavailableReason: blocker.reason, floatDownUnavailableReason: blocker.reason, sourceRef: 'lock-period-alternate-ref-required', evidenceRefs: ['lock-period-alternate-ref-required'] },
    ],
    metadata: { lockPeriodSourceRef: 'lock-period-ref-required', pricingSourceRef: 'pricing-ref-required', extensionSourceRef: 'extension-policy-ref-required', floatDownSourceRef: 'float-down-policy-ref-required', exportRef: 'export-ref-required' },
    exportRefs: ['lock-period-export-ref-required'],
    replayRefs: ['lock-period-replay-ref-required'],
    auditRefs: ['lock-period-audit-ref-required'],
    blockers: [blocker],
    fallbackReason: blocker.reason,
    uiTraceId: 'sa-s33-local-synthetic-trace',
    events: ['lock-period-comparison.synthetic.fixture.loaded'],
  };
}
