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
