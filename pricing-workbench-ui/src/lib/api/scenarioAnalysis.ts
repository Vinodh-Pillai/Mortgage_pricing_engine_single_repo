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
  const response = await fetchImpl(`/api/v1/tenants/${encodeURIComponent(tenantId)}/quote-runs/${encodeURIComponent(runId)}/what-if/workspace`, {
    headers: scenarioAnalysisHeaders,
  });
  if (response.status >= 500) throw new Error('BFF scenario analysis workspace boundary is temporarily unavailable.');
  return (await response.json()) as ScenarioAnalysisWorkspaceView;
}

export async function fetchFicoSensitivity(
  tenantId: string,
  runId: string,
  fetchImpl: typeof fetch = fetch,
): Promise<FicoSensitivityView> {
  const response = await fetchImpl(`/api/v1/tenants/${encodeURIComponent(tenantId)}/quote-runs/${encodeURIComponent(runId)}/what-if/fico-sensitivity`, {
    headers: scenarioAnalysisHeaders,
  });
  if (response.status >= 500) throw new Error('BFF FICO sensitivity boundary is temporarily unavailable.');
  return (await response.json()) as FicoSensitivityView;
}

export async function fetchLtvSensitivity(
  tenantId: string,
  runId: string,
  fetchImpl: typeof fetch = fetch,
): Promise<LtvSensitivityView> {
  const response = await fetchImpl(`/api/v1/tenants/${encodeURIComponent(tenantId)}/quote-runs/${encodeURIComponent(runId)}/what-if/ltv-sensitivity`, {
    headers: scenarioAnalysisHeaders,
  });
  if (response.status >= 500) throw new Error('BFF LTV sensitivity boundary is temporarily unavailable.');
  return (await response.json()) as LtvSensitivityView;
}

export async function fetchProductComparison(
  tenantId: string,
  runId: string,
  fetchImpl: typeof fetch = fetch,
): Promise<ProductComparisonView> {
  const response = await fetchImpl(`/api/v1/tenants/${encodeURIComponent(tenantId)}/quote-runs/${encodeURIComponent(runId)}/what-if/product-comparison`, {
    headers: scenarioAnalysisHeaders,
  });
  if (response.status >= 500) throw new Error('BFF product comparison boundary is temporarily unavailable.');
  return (await response.json()) as ProductComparisonView;
}

export async function fetchLockPeriodComparison(
  tenantId: string,
  runId: string,
  fetchImpl: typeof fetch = fetch,
): Promise<LockPeriodComparisonView> {
  const response = await fetchImpl(`/api/v1/tenants/${encodeURIComponent(tenantId)}/quote-runs/${encodeURIComponent(runId)}/what-if/lock-period-comparison`, {
    headers: scenarioAnalysisHeaders,
  });
  if (response.status >= 500) throw new Error('BFF lock period comparison boundary is temporarily unavailable.');
  return (await response.json()) as LockPeriodComparisonView;
}

export async function recalculateScenarioAnalysis(
  tenantId: string,
  runId: string,
  request: ScenarioRecalculationRequest,
  fetchImpl: typeof fetch = fetch,
): Promise<ScenarioRecalculationResult> {
  const response = await fetchImpl(`/api/v1/tenants/${encodeURIComponent(tenantId)}/quote-runs/${encodeURIComponent(runId)}/what-if/recalculate`, {
    method: 'POST',
    headers: scenarioAnalysisHeaders,
    body: JSON.stringify(request),
  });
  if (response.status >= 500) throw new Error('BFF scenario analysis recalculation boundary is temporarily unavailable.');
  return (await response.json()) as ScenarioRecalculationResult;
}
